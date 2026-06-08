#!/usr/bin/env python3
"""
FlowerShow local video preprocessing tool.

What it does:
  1. Probe each source video with ffprobe.
  2. Treat the real display short side as the highest selectable quality.
  3. Generate only lower qualities, never upscale.
  4. Preserve portrait/landscape aspect ratio and account for rotation metadata.
  5. Update quality_urls in JSONL/JSON metadata atomically.
  6. Write structured logs and retry failed ffmpeg jobs.

Default input layout:
  D:/MediaCrawler/MediaCrawler/data/douyin/videos/<aweme_id>/video.mp4

Examples:
  python tools/preprocess_videos.py
  python tools/preprocess_videos.py --transcode --limit 5
  python tools/preprocess_videos.py --transcode --force --retries 2
  python tools/preprocess_videos.py --video-dir D:/videos --json-path app/src/main/assets/video_data.jsonl
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_VIDEO_DIR = Path(r"D:\MediaCrawler\MediaCrawler\data\douyin\videos")
DEFAULT_JSON_PATH = REPO_ROOT / "app" / "src" / "main" / "assets" / "video_data.jsonl"
DEFAULT_LOG_DIR = REPO_ROOT / "tools" / "logs"

STANDARD_QUALITY_TIERS = [2160, 1440, 1080, 720, 480, 360]

BITRATE_KBPS = {
    2160: 16_000,
    1440: 8_000,
    1080: 4_500,
    720: 2_500,
    480: 1_200,
    360: 800,
}


@dataclass(frozen=True)
class VideoMeta:
    path: Path
    aweme_id: str
    coded_width: int
    coded_height: int
    display_width: int
    display_height: int
    rotation_degrees: int
    bitrate_kbps: int
    fps: float
    duration_s: float
    codec: str

    @property
    def is_portrait(self) -> bool:
        return self.display_height > self.display_width

    @property
    def short_side(self) -> int:
        return min(self.display_width, self.display_height)

    @property
    def long_side(self) -> int:
        return max(self.display_width, self.display_height)

    @property
    def source_label(self) -> str:
        return quality_label(self.short_side)

    def quality_tiers(self) -> list[int]:
        if self.short_side <= 0:
            return []
        lower = [tier for tier in STANDARD_QUALITY_TIERS if tier < self.short_side]
        return [self.short_side] + lower


@dataclass
class TranscodeResult:
    tier: int
    output_path: Path
    success: bool
    attempts: int = 0
    error: str = ""


@dataclass
class CoverResult:
    output_path: Path
    success: bool
    error: str = ""


@dataclass
class VideoReport:
    aweme_id: str
    source_path: Path
    meta: VideoMeta | None = None
    quality_urls: dict[str, str] = field(default_factory=dict)
    transcodes: list[TranscodeResult] = field(default_factory=list)
    cover: CoverResult | None = None
    error: str = ""

    @property
    def ok(self) -> bool:
        return self.meta is not None and not self.error

    @property
    def failed_transcodes(self) -> list[TranscodeResult]:
        return [result for result in self.transcodes if not result.success]


class JsonlLogger:
    def __init__(self, log_dir: Path) -> None:
        log_dir.mkdir(parents=True, exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        self.path = log_dir / f"preprocess_videos_{timestamp}.jsonl"

    def event(self, event: str, **payload: Any) -> None:
        record = {
            "ts": datetime.now().isoformat(timespec="seconds"),
            "event": event,
            **payload,
        }
        with self.path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")


def quality_label(short_side: int) -> str:
    return f"{short_side}p"


def relative_video_url(aweme_id: str, filename: str) -> str:
    return f"videos/{aweme_id}/{filename}"


def run_command(
    command: list[str],
    timeout_s: int,
    logger: JsonlLogger | None,
    event: str,
    **payload: Any,
) -> subprocess.CompletedProcess[str]:
    if logger:
        logger.event(f"{event}_start", command=command, **payload)
    result = subprocess.run(command, capture_output=True, text=True, timeout=timeout_s)
    if logger:
        logger.event(
            f"{event}_end",
            returncode=result.returncode,
            stderr_tail=last_non_empty_line(result.stderr),
            **payload,
        )
    return result


def last_non_empty_line(text: str) -> str:
    for line in reversed(text.splitlines()):
        stripped = line.strip()
        if stripped:
            return stripped[:500]
    return ""


def run_ffprobe(video_path: Path, logger: JsonlLogger | None = None) -> dict[str, Any] | None:
    command = [
        "ffprobe",
        "-v",
        "quiet",
        "-print_format",
        "json",
        "-show_format",
        "-show_streams",
        str(video_path),
    ]
    try:
        result = run_command(
            command,
            timeout_s=45,
            logger=logger,
            event="ffprobe",
            path=str(video_path),
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        if logger:
            logger.event("ffprobe_exception", path=str(video_path), error=str(exc))
        return None

    if result.returncode != 0:
        return None
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError:
        if logger:
            logger.event("ffprobe_json_error", path=str(video_path))
        return None


def detect_video(video_path: Path, logger: JsonlLogger | None = None) -> VideoMeta | None:
    data = run_ffprobe(video_path, logger)
    if not data:
        return None

    stream = next(
        (item for item in data.get("streams", []) if item.get("codec_type") == "video"),
        None,
    )
    if not stream:
        return None

    coded_width = int(stream.get("width") or 0)
    coded_height = int(stream.get("height") or 0)
    rotation = parse_rotation_degrees(stream)
    if abs(rotation) % 180 == 90:
        display_width, display_height = coded_height, coded_width
    else:
        display_width, display_height = coded_width, coded_height

    fmt = data.get("format", {})
    duration_s = float(fmt.get("duration") or stream.get("duration") or 0)
    bitrate_kbps = int(fmt.get("bit_rate") or stream.get("bit_rate") or 0) // 1000

    meta = VideoMeta(
        path=video_path,
        aweme_id=video_path.parent.name,
        coded_width=coded_width,
        coded_height=coded_height,
        display_width=display_width,
        display_height=display_height,
        rotation_degrees=rotation,
        bitrate_kbps=bitrate_kbps,
        fps=parse_fps(stream.get("r_frame_rate") or stream.get("avg_frame_rate")),
        duration_s=round(duration_s, 2),
        codec=stream.get("codec_name") or "unknown",
    )
    if logger:
        logger.event("video_detected", aweme_id=meta.aweme_id, meta=meta_to_dict(meta))
    return meta


def parse_rotation_degrees(stream: dict[str, Any]) -> int:
    tags = stream.get("tags") or {}
    raw_rotate = tags.get("rotate")
    if raw_rotate is not None:
        try:
            return normalize_rotation(int(float(raw_rotate)))
        except ValueError:
            pass

    for side_data in stream.get("side_data_list") or []:
        raw_rotation = side_data.get("rotation")
        if raw_rotation is not None:
            try:
                return normalize_rotation(int(float(raw_rotation)))
            except ValueError:
                pass
    return 0


def normalize_rotation(rotation: int) -> int:
    rotation %= 360
    if rotation > 180:
        rotation -= 360
    return rotation


def parse_fps(value: str | None) -> float:
    if not value:
        return 0.0
    try:
        numerator, denominator = value.split("/", maxsplit=1)
        denominator_float = float(denominator)
        if denominator_float == 0:
            return 0.0
        return round(float(numerator) / denominator_float, 2)
    except (ValueError, ZeroDivisionError):
        return 0.0


def meta_to_dict(meta: VideoMeta) -> dict[str, Any]:
    return {
        "path": str(meta.path),
        "aweme_id": meta.aweme_id,
        "coded_width": meta.coded_width,
        "coded_height": meta.coded_height,
        "display_width": meta.display_width,
        "display_height": meta.display_height,
        "rotation_degrees": meta.rotation_degrees,
        "short_side": meta.short_side,
        "long_side": meta.long_side,
        "bitrate_kbps": meta.bitrate_kbps,
        "fps": meta.fps,
        "duration_s": meta.duration_s,
        "codec": meta.codec,
    }


def build_scale_filter(meta: VideoMeta, target_short_side: int) -> str:
    if target_short_side >= meta.short_side:
        raise ValueError(
            f"refusing to upscale or re-label source: target={target_short_side}, "
            f"source_short_side={meta.short_side}",
        )
    if meta.is_portrait:
        return f"scale={target_short_side}:-2"
    return f"scale=-2:{target_short_side}"


def bitrate_for_tier(tier: int) -> int:
    if tier in BITRATE_KBPS:
        return BITRATE_KBPS[tier]
    return max(500, int(tier * 2.0))


def validate_video(
    video_path: Path,
    expected_short_side: int = 0,
    logger: JsonlLogger | None = None,
) -> bool:
    meta = detect_video(video_path, logger)
    if not meta:
        return False
    if meta.duration_s <= 0:
        return False
    if expected_short_side > 0 and meta.short_side != expected_short_side:
        if logger:
            logger.event(
                "validation_resolution_mismatch",
                path=str(video_path),
                expected_short_side=expected_short_side,
                actual_short_side=meta.short_side,
            )
        return False
    return True


def transcode_video(
    source: Path,
    meta: VideoMeta,
    target_short_side: int,
    force: bool,
    retries: int,
    logger: JsonlLogger,
) -> TranscodeResult:
    final_path = source.parent / f"video_{target_short_side}p.mp4"

    if final_path.exists():
        if not force and validate_video(final_path, target_short_side, logger):
            logger.event(
                "transcode_skip_existing",
                aweme_id=meta.aweme_id,
                tier=target_short_side,
                output=str(final_path),
            )
            return TranscodeResult(
                tier=target_short_side,
                output_path=final_path,
                success=True,
                attempts=0,
            )
        final_path.unlink()

    try:
        scale_filter = build_scale_filter(meta, target_short_side)
    except ValueError as exc:
        return TranscodeResult(
            tier=target_short_side,
            output_path=final_path,
            success=False,
            error=str(exc),
        )

    bitrate = bitrate_for_tier(target_short_side)
    tmp_path = final_path.with_suffix(".tmp.mp4")
    command = [
        "ffmpeg",
        "-y",
        "-hide_banner",
        "-i",
        str(source),
        "-map",
        "0:v:0",
        "-map",
        "0:a?",
        "-vf",
        scale_filter,
        "-c:v",
        "libx264",
        "-preset",
        "fast",
        "-crf",
        "23",
        "-b:v",
        f"{bitrate}k",
        "-maxrate",
        f"{int(bitrate * 1.5)}k",
        "-bufsize",
        f"{bitrate * 2}k",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-b:a",
        "128k",
        "-movflags",
        "+faststart",
        str(tmp_path),
    ]

    attempts = retries + 1
    last_error = ""
    for attempt in range(1, attempts + 1):
        tmp_path.unlink(missing_ok=True)
        try:
            started = time.time()
            result = run_command(
                command,
                timeout_s=900,
                logger=logger,
                event="ffmpeg_transcode",
                aweme_id=meta.aweme_id,
                tier=target_short_side,
                attempt=attempt,
            )
            elapsed_s = round(time.time() - started, 2)
        except (OSError, subprocess.TimeoutExpired) as exc:
            last_error = str(exc)
            logger.event(
                "ffmpeg_transcode_exception",
                aweme_id=meta.aweme_id,
                tier=target_short_side,
                attempt=attempt,
                error=last_error,
            )
            continue

        if result.returncode != 0:
            last_error = last_non_empty_line(result.stderr) or "ffmpeg failed"
            logger.event(
                "ffmpeg_transcode_failed",
                aweme_id=meta.aweme_id,
                tier=target_short_side,
                attempt=attempt,
                error=last_error,
            )
            continue

        if not validate_video(tmp_path, target_short_side, logger):
            last_error = "output validation failed"
            logger.event(
                "ffmpeg_transcode_validation_failed",
                aweme_id=meta.aweme_id,
                tier=target_short_side,
                attempt=attempt,
            )
            continue

        tmp_path.replace(final_path)
        logger.event(
            "ffmpeg_transcode_success",
            aweme_id=meta.aweme_id,
            tier=target_short_side,
            attempt=attempt,
            elapsed_s=elapsed_s,
            output=str(final_path),
            size_bytes=final_path.stat().st_size,
        )
        return TranscodeResult(
            tier=target_short_side,
            output_path=final_path,
            success=True,
            attempts=attempt,
        )

    tmp_path.unlink(missing_ok=True)
    return TranscodeResult(
        tier=target_short_side,
        output_path=final_path,
        success=False,
        attempts=attempts,
        error=last_error,
    )


def generate_cover_thumbnail(
    source: Path,
    meta: VideoMeta,
    force: bool,
    logger: JsonlLogger,
) -> CoverResult:
    output_path = source.parent / "cover_360.jpg"
    if output_path.exists() and not force:
        return CoverResult(output_path=output_path, success=True)

    seek_s = min(max(meta.duration_s * 0.1, 0.5), 2.0)
    if meta.is_portrait:
        scale_filter = "scale=360:-2"
    else:
        scale_filter = "scale=-2:360"
    tmp_path = output_path.with_suffix(".tmp.jpg")
    command = [
        "ffmpeg",
        "-y",
        "-hide_banner",
        "-ss",
        f"{seek_s:.2f}",
        "-i",
        str(source),
        "-frames:v",
        "1",
        "-vf",
        scale_filter,
        "-q:v",
        "3",
        str(tmp_path),
    ]
    try:
        result = run_command(
            command,
            timeout_s=120,
            logger=logger,
            event="ffmpeg_cover",
            aweme_id=meta.aweme_id,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        tmp_path.unlink(missing_ok=True)
        return CoverResult(output_path=output_path, success=False, error=str(exc))

    if result.returncode != 0 or not tmp_path.exists() or tmp_path.stat().st_size == 0:
        tmp_path.unlink(missing_ok=True)
        return CoverResult(
            output_path=output_path,
            success=False,
            error=last_non_empty_line(result.stderr) or "cover generation failed",
        )
    tmp_path.replace(output_path)
    logger.event(
        "ffmpeg_cover_success",
        aweme_id=meta.aweme_id,
        output=str(output_path),
        size_bytes=output_path.stat().st_size,
    )
    return CoverResult(output_path=output_path, success=True)


def process_video(
    aweme_dir: Path,
    transcode: bool,
    force: bool,
    retries: int,
    generate_covers: bool,
    logger: JsonlLogger,
) -> VideoReport:
    source = aweme_dir / "video.mp4"
    report = VideoReport(aweme_id=aweme_dir.name, source_path=source)
    if not source.exists():
        report.error = "video.mp4 not found"
        logger.event("video_missing", aweme_id=aweme_dir.name, path=str(source))
        return report

    meta = detect_video(source, logger)
    if not meta or meta.short_side <= 0:
        report.error = "ffprobe detection failed"
        logger.event("video_detection_failed", aweme_id=aweme_dir.name, path=str(source))
        return report
    report.meta = meta

    tiers = meta.quality_tiers()
    report.quality_urls[meta.source_label] = relative_video_url(meta.aweme_id, "video.mp4")

    if generate_covers and transcode:
        report.cover = generate_cover_thumbnail(source, meta, force, logger)

    if not transcode:
        for tier in tiers[1:]:
            report.quality_urls[quality_label(tier)] = relative_video_url(
                meta.aweme_id,
                f"video_{tier}p.mp4",
            )
        return report

    for tier in tiers[1:]:
        result = transcode_video(source, meta, tier, force, retries, logger)
        report.transcodes.append(result)
        if result.success:
            report.quality_urls[quality_label(tier)] = relative_video_url(
                meta.aweme_id,
                result.output_path.name,
            )

    logger.event(
        "video_processed",
        aweme_id=meta.aweme_id,
        qualities=report.quality_urls,
        failed_transcodes=[
            {"tier": item.tier, "error": item.error} for item in report.failed_transcodes
        ],
    )
    return report


def discover_video_dirs(video_dir: Path) -> list[Path]:
    return sorted(
        child
        for child in video_dir.iterdir()
        if child.is_dir() and (child / "video.mp4").exists()
    )


def update_metadata_file(
    metadata_path: Path,
    quality_updates: dict[str, dict[str, str]],
    cover_updates: dict[str, str],
    logger: JsonlLogger,
) -> int:
    if not quality_updates and not cover_updates:
        return 0
    if not metadata_path.exists():
        logger.event("metadata_missing", path=str(metadata_path))
        return 0

    raw = metadata_path.read_text(encoding="utf-8-sig")
    stripped = raw.strip()
    if not stripped:
        return 0

    if stripped.startswith("["):
        data = json.loads(stripped)
        if not isinstance(data, list):
            raise ValueError("metadata JSON root must be a list")
        updated = update_metadata_entries(data, quality_updates, cover_updates)
        content = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    else:
        lines: list[str] = []
        updated = 0
        for line in raw.splitlines():
            if not line.strip():
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                lines.append(line)
                continue
            updated += update_metadata_entry(entry, quality_updates, cover_updates)
            lines.append(json.dumps(entry, ensure_ascii=False))
        content = "\n".join(lines) + "\n"

    if updated == 0:
        return 0

    tmp_path = metadata_path.with_suffix(metadata_path.suffix + ".tmp")
    tmp_path.write_text(content, encoding="utf-8")
    validate_metadata_content(tmp_path)

    backup_path = metadata_path.with_suffix(metadata_path.suffix + ".bak")
    shutil.copy2(metadata_path, backup_path)
    tmp_path.replace(metadata_path)
    logger.event(
        "metadata_updated",
        path=str(metadata_path),
        backup=str(backup_path),
        updated=updated,
    )
    return updated


def update_metadata_entries(
    entries: list[Any],
    quality_updates: dict[str, dict[str, str]],
    cover_updates: dict[str, str],
) -> int:
    updated = 0
    for entry in entries:
        if isinstance(entry, dict):
            updated += update_metadata_entry(entry, quality_updates, cover_updates)
    return updated


def update_metadata_entry(
    entry: dict[str, Any],
    quality_updates: dict[str, dict[str, str]],
    cover_updates: dict[str, str],
) -> int:
    aweme_id = entry.get("aweme_id") or entry.get("id")
    if not isinstance(aweme_id, str):
        return 0
    changed = 0
    if aweme_id in quality_updates:
        entry["quality_urls"] = quality_updates[aweme_id]
        changed = 1
    if aweme_id in cover_updates:
        entry["cover_thumbnail_url"] = cover_updates[aweme_id]
        changed = 1
    return changed


def validate_metadata_content(path: Path) -> None:
    content = path.read_text(encoding="utf-8")
    stripped = content.strip()
    if not stripped:
        return
    if stripped.startswith("["):
        json.loads(stripped)
        return
    for line_number, line in enumerate(content.splitlines(), start=1):
        if line.strip():
            try:
                json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"invalid JSONL at line {line_number}: {exc}") from exc


def print_report(reports: list[VideoReport], elapsed_s: float, log_path: Path) -> None:
    total = len(reports)
    detected = sum(1 for report in reports if report.ok)
    multi = sum(1 for report in reports if len(report.quality_urls) > 1)
    transcodes = [item for report in reports for item in report.transcodes]
    transcode_ok = sum(1 for item in transcodes if item.success)
    transcode_failed = sum(1 for item in transcodes if not item.success)

    print()
    print("=" * 72)
    print("FlowerShow video preprocessing report")
    print("=" * 72)
    print(f"Videos scanned:        {total}")
    print(f"Detected successfully: {detected}")
    print(f"Multi-quality videos:  {multi}")
    print(f"Transcodes:            {len(transcodes)} total / {transcode_ok} ok / {transcode_failed} failed")
    print(f"Elapsed:               {elapsed_s:.1f}s")
    print(f"Log:                   {log_path}")
    print()

    for report in reports:
        if not report.ok or not report.meta:
            print(f"[{report.aweme_id}] FAIL {report.error}")
            continue
        meta = report.meta
        orientation = "portrait" if meta.is_portrait else "landscape"
        failures = f", failed={len(report.failed_transcodes)}" if report.failed_transcodes else ""
        print(
            f"[{report.aweme_id}] "
            f"display={meta.display_width}x{meta.display_height}, "
            f"coded={meta.coded_width}x{meta.coded_height}, "
            f"rotation={meta.rotation_degrees}, {orientation}, "
            f"qualities={list(report.quality_urls.keys())}{failures}",
        )
        for failed in report.failed_transcodes:
            print(f"  - {quality_label(failed.tier)} failed: {failed.error[:160]}")
        if report.cover and not report.cover.success:
            print(f"  - cover failed: {report.cover.error[:160]}")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="FlowerShow video preprocessing")
    parser.add_argument("--transcode", action="store_true", help="Generate lower-quality MP4 files and update metadata")
    parser.add_argument("--force", action="store_true", help="Overwrite existing generated files")
    parser.add_argument("--retries", type=int, default=1, help="Retry failed ffmpeg jobs N times")
    parser.add_argument("--video-dir", type=Path, default=DEFAULT_VIDEO_DIR, help=f"Video root directory (default: {DEFAULT_VIDEO_DIR})")
    parser.add_argument("--json-path", type=Path, default=DEFAULT_JSON_PATH, help=f"Metadata JSONL/JSON path (default: {DEFAULT_JSON_PATH})")
    parser.add_argument("--jsonl-path", type=Path, default=None, help="Deprecated alias for --json-path")
    parser.add_argument("--limit", type=int, default=0, help="Process only first N videos")
    parser.add_argument("--log-dir", type=Path, default=DEFAULT_LOG_DIR, help=f"Log directory (default: {DEFAULT_LOG_DIR})")
    parser.add_argument("--generate-covers", action="store_true", help="Generate cover_360.jpg thumbnails and write cover_thumbnail_url")
    parser.add_argument("--fail-on-error", action="store_true", help="Exit non-zero if detection or transcoding fails")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    if args.jsonl_path is not None:
        args.json_path = args.jsonl_path

    logger = JsonlLogger(args.log_dir)
    logger.event(
        "run_started",
        video_dir=str(args.video_dir),
        json_path=str(args.json_path),
        transcode=args.transcode,
        force=args.force,
        retries=args.retries,
        generate_covers=args.generate_covers,
    )

    if not args.video_dir.exists():
        print(f"[ERROR] Video directory not found: {args.video_dir}")
        logger.event("run_failed", error="video_dir_not_found")
        return 2

    video_dirs = discover_video_dirs(args.video_dir)
    if args.limit > 0:
        video_dirs = video_dirs[: args.limit]
    if not video_dirs:
        print(f"[ERROR] No <aweme_id>/video.mp4 files found under {args.video_dir}")
        logger.event("run_failed", error="no_videos_found")
        return 2

    print(f"Video dir:  {args.video_dir}")
    print(f"JSON path:  {args.json_path}")
    print(f"Log file:   {logger.path}")
    print(f"Mode:       {'TRANSCODE' if args.transcode else 'DRY RUN'}")
    print(f"Videos:     {len(video_dirs)}")

    started = time.time()
    reports: list[VideoReport] = []
    quality_updates: dict[str, dict[str, str]] = {}
    cover_updates: dict[str, str] = {}

    for index, aweme_dir in enumerate(video_dirs, start=1):
        print(f"[{index}/{len(video_dirs)}] {aweme_dir.name}")
        report = process_video(
            aweme_dir=aweme_dir,
            transcode=args.transcode,
            force=args.force,
            retries=max(args.retries, 0),
            generate_covers=args.generate_covers,
            logger=logger,
        )
        reports.append(report)
        if report.ok and args.transcode and len(report.quality_urls) > 1:
            quality_updates[report.aweme_id] = report.quality_urls
        if report.cover and report.cover.success:
            cover_updates[report.aweme_id] = relative_video_url(
                report.aweme_id,
                report.cover.output_path.name,
            )

    updated = 0
    if args.transcode:
        updated = update_metadata_file(args.json_path, quality_updates, cover_updates, logger)
        print(f"Metadata updated entries: {updated}")

    elapsed_s = time.time() - started
    print_report(reports, elapsed_s, logger.path)
    logger.event(
        "run_finished",
        elapsed_s=round(elapsed_s, 2),
        scanned=len(reports),
        metadata_updated=updated,
        detection_failures=sum(1 for report in reports if not report.ok),
        transcode_failures=sum(len(report.failed_transcodes) for report in reports),
    )

    if args.fail_on_error:
        has_error = any(not report.ok or report.failed_transcodes for report in reports)
        if has_error:
            return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
