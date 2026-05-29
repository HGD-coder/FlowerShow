#!/usr/bin/env python3
"""
Video multi-quality preprocessing tool / 本地视频多清晰度预处理工具

Scans local video directory, detects resolution via ffprobe (using min(width,height)
as the quality-defining dimension), generates lower-resolution versions via ffmpeg
with correct orientation handling, and updates JSONL metadata atomically.

Usage:
  python preprocess_videos.py                                    # dry-run report
  python preprocess_videos.py --transcode                         # transcode + update
  python preprocess_videos.py --transcode --force                 # overwrite existing
  python preprocess_videos.py --video-dir D:\videos --limit 5     # first 5 videos only
  python preprocess_videos.py --jsonl-path D:\other\data.jsonl   # custom JSONL path
"""

import json, sys, argparse, time, shutil, tempfile, subprocess
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional

# ── Config (overridable via CLI) ────────────────────────────────────────
DEFAULT_VIDEO_DIR = Path(r"D:\MediaCrawler\MediaCrawler\data\douyin\videos")
DEFAULT_JSONL_PATH = Path(r"D:\android-studio\flowershow\app\src\main\assets\video_data.jsonl")

# Quality tiers: defined by the shorter side (min(width, height))
ALL_QUALITY_TIERS = [2160, 1440, 1080, 720, 480, 360]

# Bitrate targets (kbps) for each quality tier
BITRATE_KBPS = {2160: 8000, 1440: 4000, 1080: 2500, 720: 1500, 480: 900, 360: 500}


# ══════════════════════════════════════════════════════════════════════════
# Data models
# ══════════════════════════════════════════════════════════════════════════

@dataclass
class VideoMeta:
    """Video metadata from ffprobe."""
    path: Path
    aweme_id: str
    width: int = 0
    height: int = 0
    bitrate_kbps: int = 0
    fps: float = 0.0
    duration_s: float = 0.0
    codec: str = "unknown"

    @property
    def is_portrait(self) -> bool:
        return self.height > self.width

    @property
    def quality_dim(self) -> int:
        """The quality-defining dimension: min(width, height).
        For 720x1280 (vertical) → 720p. For 1280x720 (horizontal) → 720p."""
        return min(self.width, self.height)

    def available_tiers(self) -> list[int]:
        """Available tiers: predefined tiers <= quality_dim, plus quality_dim as max."""
        tiers = [t for t in ALL_QUALITY_TIERS if t < self.quality_dim]
        tiers.append(self.quality_dim)
        return sorted(set(tiers), reverse=True)


@dataclass
class TranscodeResult:
    height: int
    output_path: Path
    success: bool = False
    error: str = ""


@dataclass
class VideoReport:
    aweme_id: str
    meta: Optional[VideoMeta] = None
    transcodes: list[TranscodeResult] = field(default_factory=list)
    quality_urls: dict[str, str] = field(default_factory=dict)
    error: str = ""

    @property
    def ok(self) -> bool:
        return self.meta is not None and not self.error


# ══════════════════════════════════════════════════════════════════════════
# ffprobe
# ══════════════════════════════════════════════════════════════════════════

def run_ffprobe(video_path: Path) -> Optional[dict]:
    cmd = ["ffprobe", "-v", "quiet", "-print_format", "json",
           "-show_format", "-show_streams", str(video_path)]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if r.returncode != 0:
            print(f"      [WARN] ffprobe stderr: {r.stderr[:200]}")
            return None
        return json.loads(r.stdout)
    except Exception as e:
        print(f"      [ERROR] ffprobe: {e}")
        return None


def detect_video(video_path: Path) -> Optional[VideoMeta]:
    aweme_id = video_path.parent.name
    data = run_ffprobe(video_path)
    if data is None:
        return None

    video_stream = next((s for s in data.get("streams", [])
                          if s.get("codec_type") == "video"), None)
    if video_stream is None:
        print(f"      [WARN] No video stream in {video_path}")
        return None

    fmt = data.get("format", {})
    duration = float(fmt.get("duration") or video_stream.get("duration") or 0)
    bitrate = int(fmt.get("bit_rate") or video_stream.get("bit_rate") or 0) // 1000

    fps = 0.0
    fps_str = video_stream.get("r_frame_rate", "0/1")
    try:
        num, den = fps_str.split("/")
        fps = float(num) / float(den) if float(den) != 0 else 0.0
    except (ValueError, ZeroDivisionError):
        pass

    return VideoMeta(
        path=video_path, aweme_id=aweme_id,
        width=int(video_stream.get("width", 0)),
        height=int(video_stream.get("height", 0)),
        bitrate_kbps=bitrate, fps=round(fps, 2),
        duration_s=round(duration, 1),
        codec=video_stream.get("codec_name", "unknown"),
    )


def validate_video(video_path: Path, expected_short_side: int = 0) -> bool:
    """Check if an existing video file is valid: playable AND has correct resolution."""
    data = run_ffprobe(video_path)
    if data is None:
        return False
    streams = data.get("streams", [])
    video_stream = next((s for s in streams if s.get("codec_type") == "video"), None)
    if video_stream is None:
        return False
    has_duration = float(data.get("format", {}).get("duration", 0) or 0) > 0
    if not has_duration:
        return False
    # Check resolution if expected
    if expected_short_side > 0:
        actual_short = min(int(video_stream.get("width", 0)), int(video_stream.get("height", 0)))
        if actual_short != expected_short_side:
            return False
    return True


# ══════════════════════════════════════════════════════════════════════════
# ffmpeg transcode
# ══════════════════════════════════════════════════════════════════════════

def build_scale_filter(meta: VideoMeta, target_short_side: int) -> str:
    """Build correct scale filter for video orientation.
    For portrait (e.g. 720x1280 → 720p): scale=720:-2  (constrain width)
    For landscape (e.g. 1280x720 → 720p): scale=-2:720 (constrain height)
    """
    if meta.is_portrait:
        return f"scale={target_short_side}:-2"
    else:
        return f"scale=-2:{target_short_side}"


def transcode_video(source: Path, meta: VideoMeta,
                    target_short_side: int, force: bool = False) -> TranscodeResult:
    """Transcode to target short-side resolution. Writes to temp file first,
    validates with ffprobe, then renames to final name."""
    final_path = source.parent / f"video_{target_short_side}p.mp4"

    # Check existing
    if final_path.exists():
        if not force and validate_video(final_path, target_short_side):
            print(f"      [SKIP] already exists and valid")
            return TranscodeResult(height=target_short_side, output_path=final_path, success=True)
        elif force:
            final_path.unlink()
            print(f"      [OVERWRITE] forcing re-transcode")
        else:
            print(f"      [WARN] exists but corrupted, re-transcoding ...")
            final_path.unlink()

    bitrate = BITRATE_KBPS.get(target_short_side, int(target_short_side * 1.5))
    scale = build_scale_filter(meta, target_short_side)
    orientation = "portrait" if meta.is_portrait else "landscape"
    print(f"      Transcoding → {target_short_side}p "
          f"({scale}, {orientation}, {bitrate}kbps) ...", end=" ", flush=True)

    # Write to temp file first
    tmp_path = final_path.with_suffix(".tmp.mp4")
    start = time.time()

    cmd = [
        "ffmpeg", "-y", "-i", str(source),
        "-vf", scale,
        "-c:v", "libx264", "-preset", "fast", "-crf", "23",
        "-b:v", f"{bitrate}k", "-maxrate", f"{int(bitrate * 1.5)}k",
        "-bufsize", f"{int(bitrate * 2)}k",
        "-c:a", "aac", "-b:a", "128k",
        "-movflags", "+faststart",
        str(tmp_path),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    elapsed = time.time() - start

    if result.returncode != 0:
        # Clean up failed temp file
        tmp_path.unlink(missing_ok=True)
        err = result.stderr.strip().split("\n")[-1] if result.stderr.strip() else "ffmpeg error"
        print(f"FAIL: {err[:120]}")
        return TranscodeResult(height=target_short_side, output_path=final_path,
                               success=False, error=err)

    # Validate the output
    if not validate_video(tmp_path):
        tmp_path.unlink(missing_ok=True)
        print(f"FAIL: output validation failed (corrupt file)")
        return TranscodeResult(height=target_short_side, output_path=final_path,
                               success=False, error="validation failed")

    # Atomically move temp → final
    tmp_path.replace(final_path)
    size_mb = final_path.stat().st_size / (1024 * 1024)
    print(f"OK ({elapsed:.0f}s, {size_mb:.1f}MB)")
    return TranscodeResult(height=target_short_side, output_path=final_path, success=True)


# ══════════════════════════════════════════════════════════════════════════
# JSONL update (atomic: temp → validate → replace + backup)
# ══════════════════════════════════════════════════════════════════════════

def update_jsonl_batch(jsonl_path: Path, updates: dict[str, dict[str, str]]):
    """Atomically update quality_urls for multiple videos in JSONL.
    Writes to temp file, validates, backs up original, then replaces."""
    if not updates:
        return
    if not jsonl_path.exists():
        print(f"  [WARN] JSONL not found: {jsonl_path}")
        return

    # Read
    lines = jsonl_path.read_text(encoding="utf-8").strip().split("\n")
    updated_count = 0
    new_lines = []

    for line in lines:
        line = line.strip()
        if not line:
            continue
        entry = None
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            new_lines.append(line)
            continue

        aweme_id = entry.get("aweme_id")
        if aweme_id in updates:
            entry["quality_urls"] = updates[aweme_id]
            updated_count += 1

        new_lines.append(json.dumps(entry, ensure_ascii=False))

    if updated_count == 0:
        return

    content = "\n".join(new_lines) + "\n"

    # Write to temp file
    tmp_path = jsonl_path.with_suffix(".tmp")
    tmp_path.write_text(content, encoding="utf-8")

    # Validate: every line is valid JSON
    for i, line in enumerate(content.strip().split("\n")):
        if not line.strip():
            continue
        try:
            json.loads(line)
        except json.JSONDecodeError as e:
            print(f"  [ERROR] JSONL validation failed at line {i+1}: {e}")
            tmp_path.unlink(missing_ok=True)
            return

    # Backup + replace
    bak_path = jsonl_path.with_suffix(".jsonl.bak")
    shutil.copy2(jsonl_path, bak_path)
    tmp_path.replace(jsonl_path)

    print(f"  [JSONL] Updated {updated_count} videos (backup: {bak_path.name})")
    for aweme_id, urls in updates.items():
        print(f"    {aweme_id}: {urls}")


# ══════════════════════════════════════════════════════════════════════════
# Pipeline
# ══════════════════════════════════════════════════════════════════════════

def process_video(aweme_dir: Path, transcode: bool = False,
                  force: bool = False) -> VideoReport:
    aweme_id = aweme_dir.name
    video_file = aweme_dir / "video.mp4"
    report = VideoReport(aweme_id=aweme_id)

    if not video_file.exists():
        report.error = "video.mp4 not found"
        return report

    meta = detect_video(video_file)
    if meta is None:
        report.error = "ffprobe detection failed"
        return report
    report.meta = meta

    tiers = meta.available_tiers()
    orientation = "vertical" if meta.is_portrait else "horizontal"
    print(f"  Source: {meta.width}x{meta.height} ({orientation}) {meta.codec} "
          f"{meta.bitrate_kbps}kbps {meta.fps}fps {meta.duration_s}s")
    print(f"  Quality-defining dim: {meta.quality_dim}p")
    print(f"  Available tiers: {[f'{t}p' for t in tiers]}")

    if not transcode:
        for tier in tiers:
            filename = "video.mp4" if tier == tiers[0] else f"video_{tier}p.mp4"
            report.quality_urls[f"{tier}p"] = f"videos/{aweme_id}/{filename}"
        return report

    # Transcode: source (tier[0]) = original file; lower tiers = encode
    for i, tier in enumerate(tiers):
        if i == 0:
            report.quality_urls[f"{tier}p"] = f"videos/{aweme_id}/video.mp4"
            continue
        result = transcode_video(video_file, meta, tier, force)
        report.transcodes.append(result)
        if result.success:
            report.quality_urls[f"{tier}p"] = f"videos/{aweme_id}/{result.output_path.name}"

    return report


def print_summary(reports: list[VideoReport], elapsed: float):
    total = len(reports)
    ok = sum(1 for r in reports if r.ok)
    failed = total - ok
    multi = sum(1 for r in reports if len(r.quality_urls) > 1)
    total_transcodes = sum(len(r.transcodes) for r in reports)
    trans_ok = sum(1 for r in reports for t in r.transcodes if t.success)
    trans_fail = sum(1 for r in reports for t in r.transcodes if not t.success)

    print("\n" + "=" * 60)
    print("PROCESSING REPORT")
    print("=" * 60)
    print(f"  Videos scanned:         {total}")
    print(f"  Detected successfully:  {ok}")
    print(f"  Failed detection:       {failed}")
    print(f"  Multi-quality videos:   {multi}")
    print(f"  Transcodes:             {total_transcodes} total / {trans_ok} ok / {trans_fail} failed")
    print(f"  Time elapsed:           {elapsed:.0f}s")
    print()

    for r in reports:
        if not r.ok:
            print(f"  [{r.aweme_id}] FAIL: {r.error}")
            continue
        m = r.meta
        qualities = list(r.quality_urls.keys())
        trans = f" | transcodes: {len(r.transcodes)}" if r.transcodes else ""
        print(f"  [{r.aweme_id}] {m.width}x{m.height} → {qualities}{trans}")

    failures = [(r.aweme_id, t) for r in reports for t in r.transcodes if not t.success]
    if failures:
        print(f"\n  FAILURES ({len(failures)}):")
        for aweme_id, t in failures:
            print(f"    [{aweme_id}] {t.height}p: {t.error[:100]}")


# ══════════════════════════════════════════════════════════════════════════
# Main
# ══════════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="Video multi-quality preprocessing")
    parser.add_argument("--transcode", action="store_true",
                        help="Actually transcode (default: dry-run only)")
    parser.add_argument("--force", action="store_true",
                        help="Overwrite existing transcoded files")
    parser.add_argument("--video-dir", type=Path, default=DEFAULT_VIDEO_DIR,
                        help=f"Video directory (default: {DEFAULT_VIDEO_DIR})")
    parser.add_argument("--jsonl-path", type=Path, default=DEFAULT_JSONL_PATH,
                        help=f"JSONL path (default: {DEFAULT_JSONL_PATH})")
    parser.add_argument("--limit", type=int, default=0,
                        help="Process only first N videos (0 = all)")
    args = parser.parse_args()

    video_dir = Path(args.video_dir)
    jsonl_path = Path(args.jsonl_path)

    if not video_dir.exists():
        print(f"[ERROR] Video directory not found: {video_dir}")
        sys.exit(1)

    aweme_dirs = sorted(
        d for d in video_dir.iterdir()
        if d.is_dir() and (d / "video.mp4").exists()
    )
    if not aweme_dirs:
        print(f"[ERROR] No video.mp4 files found under {video_dir}")
        sys.exit(1)

    if args.limit > 0:
        aweme_dirs = aweme_dirs[:args.limit]

    mode = "TRANSCODE" if args.transcode else "DRY-RUN (scan only)"
    force = " + FORCE" if args.force else ""
    print(f"Video dir: {video_dir}")
    print(f"JSONL path: {jsonl_path}")
    print(f"Found {len(aweme_dirs)} videos. Mode: {mode}{force}\n")

    start = time.time()
    reports = []
    jsonl_updates: dict[str, dict[str, str]] = {}

    for i, d in enumerate(aweme_dirs):
        print(f"[{i+1}/{len(aweme_dirs)}] {d.name}")
        report = process_video(d, transcode=args.transcode, force=args.force)
        reports.append(report)
        if report.ok and len(report.quality_urls) > 1 and args.transcode:
            jsonl_updates[report.aweme_id] = report.quality_urls

    # Atomic batch update
    if jsonl_updates:
        print(f"\nWriting {len(jsonl_updates)} quality_urls to JSONL ...")
        update_jsonl_batch(jsonl_path, jsonl_updates)

    print_summary(reports, time.time() - start)


if __name__ == "__main__":
    main()
