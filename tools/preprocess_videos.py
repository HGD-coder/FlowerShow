#!/usr/bin/env python3
"""
Video multi-quality preprocessing tool / 本地视频多清晰度预处理工具

Scans local video directory, detects source resolution via ffprobe,
generates lower-resolution versions via ffmpeg, and updates JSONL metadata.

Usage:
  python tools/preprocess_videos.py                     # dry-run: scan + report only
  python tools/preprocess_videos.py --transcode          # scan + transcode + update JSONL
  python tools/preprocess_videos.py --transcode --force  # overwrite existing transcoded files
"""

import json, os, subprocess, sys, argparse, time
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional

# ── Config ──────────────────────────────────────────────────────────────
VIDEO_DIR = Path(r"D:\MediaCrawler\MediaCrawler\data\douyin\videos")
JSONL_PATH = Path(r"D:\android-studio\flowershow\app\src\main\assets\video_data.jsonl")

# Quality tiers: heights we can offer (only those <= source height are kept)
ALL_QUALITY_TIERS = [2160, 1440, 1080, 720, 480, 360]

# Bitrate targets: roughly proportional to resolution for good quality/size balance
BITRATE_KBPS = {2160: 8000, 1440: 4000, 1080: 2500, 720: 1500, 480: 900, 360: 500}


# ── Data models ─────────────────────────────────────────────────────────

@dataclass
class VideoMeta:
    """Raw video metadata from ffprobe."""
    path: Path
    aweme_id: str
    width: int = 0
    height: int = 0
    bitrate_kbps: int = 0
    fps: float = 0.0
    duration_s: float = 0.0
    codec: str = "unknown"

    @property
    def source_quality(self) -> str:
        return f"{self.height}p"

    def available_tiers(self) -> list[int]:
        """Quality tiers available for this video (<= source height)."""
        return [t for t in ALL_QUALITY_TIERS if t <= self.height]


@dataclass
class TranscodeResult:
    """Result of a single transcode operation."""
    height: int
    output_path: Path
    success: bool = False
    error: str = ""


@dataclass
class VideoReport:
    """Full processing report for one video."""
    aweme_id: str
    meta: Optional[VideoMeta] = None
    transcodes: list[TranscodeResult] = field(default_factory=list)
    quality_urls: dict[str, str] = field(default_factory=dict)
    error: str = ""

    @property
    def ok(self) -> bool:
        return self.meta is not None and not self.error


# ── ffprobe detection ───────────────────────────────────────────────────

def run_ffprobe(video_path: Path) -> Optional[dict]:
    """Run ffprobe and return raw JSON output."""
    cmd = [
        "ffprobe", "-v", "quiet", "-print_format", "json",
        "-show_format", "-show_streams", str(video_path),
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if result.returncode != 0:
            return None
        return json.loads(result.stdout)
    except Exception as e:
        print(f"  [ERROR] ffprobe failed: {e}")
        return None


def detect_video(video_path: Path) -> Optional[VideoMeta]:
    """Detect video metadata via ffprobe."""
    aweme_id = video_path.parent.name
    data = run_ffprobe(video_path)
    if data is None:
        return None

    # Find video stream
    video_stream = None
    for stream in data.get("streams", []):
        if stream.get("codec_type") == "video":
            video_stream = stream
            break
    if video_stream is None:
        print(f"  [WARN] No video stream found in {video_path}")
        return None

    fmt = data.get("format", {})
    duration = float(fmt.get("duration", 0)) or float(video_stream.get("duration", 0) or 0)
    bitrate = int(fmt.get("bit_rate", 0) or 0) // 1000  # bps → kbps
    if bitrate == 0:
        bitrate = int(video_stream.get("bit_rate", 0) or 0) // 1000

    # Parse frame rate (may be a fraction like "30000/1001")
    fps_str = video_stream.get("r_frame_rate", "0/1")
    try:
        num, den = fps_str.split("/")
        fps = float(num) / float(den) if float(den) != 0 else 0.0
    except (ValueError, ZeroDivisionError):
        fps = 0.0

    return VideoMeta(
        path=video_path,
        aweme_id=aweme_id,
        width=int(video_stream.get("width", 0)),
        height=int(video_stream.get("height", 0)),
        bitrate_kbps=bitrate,
        fps=round(fps, 2),
        duration_s=round(duration, 1),
        codec=video_stream.get("codec_name", "unknown"),
    )


# ── ffmpeg transcode ────────────────────────────────────────────────────

def transcode_video(source: Path, target_height: int, force: bool = False) -> TranscodeResult:
    """Transcode source video to target height using ffmpeg."""
    output_path = source.parent / f"video_{target_height}p.mp4"

    if output_path.exists() and not force:
        print(f"      [SKIP] already exists")
        return TranscodeResult(height=target_height, output_path=output_path, success=True)

    bitrate = BITRATE_KBPS.get(target_height, int(target_height * 1.5))
    print(f"      Transcoding → {target_height}p @ {bitrate}kbps ...", end=" ", flush=True)

    start = time.time()
    cmd = [
        "ffmpeg", "-y", "-i", str(source),
        "-vf", f"scale=-2:{target_height}",
        "-c:v", "libx264", "-preset", "fast", "-crf", "23",
        "-b:v", f"{bitrate}k", "-maxrate", f"{int(bitrate * 1.5)}k",
        "-bufsize", f"{int(bitrate * 2)}k",
        "-c:a", "aac", "-b:a", "128k",
        "-movflags", "+faststart",
        str(output_path),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    elapsed = time.time() - start

    if result.returncode == 0:
        size_mb = output_path.stat().st_size / (1024 * 1024)
        print(f"OK ({elapsed:.0f}s, {size_mb:.1f}MB)")
        return TranscodeResult(height=target_height, output_path=output_path, success=True)
    else:
        err = result.stderr.strip().split("\n")[-1] if result.stderr.strip() else "unknown error"
        print(f"FAIL: {err[:120]}")
        return TranscodeResult(height=target_height, output_path=output_path, success=False, error=err)


# ── JSONL update ────────────────────────────────────────────────────────

def update_jsonl(aweme_id: str, quality_urls: dict[str, str]):
    """Update quality_urls field for a specific video in the JSONL file."""
    if not JSONL_PATH.exists():
        print(f"  [WARN] JSONL not found: {JSONL_PATH}")
        return

    lines = JSONL_PATH.read_text(encoding="utf-8").strip().split("\n")
    updated = 0
    new_lines = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            new_lines.append(line)
            continue

        if entry.get("aweme_id") == aweme_id:
            entry["quality_urls"] = quality_urls
            updated += 1

        new_lines.append(json.dumps(entry, ensure_ascii=False))

    if updated > 0:
        JSONL_PATH.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
        print(f"  [JSONL] Updated {aweme_id}: {quality_urls}")
    else:
        print(f"  [JSONL] WARN: {aweme_id} not found in JSONL (quality_urls not written)")


# ── Main pipeline ───────────────────────────────────────────────────────

def process_video(aweme_dir: Path, transcode: bool = False, force: bool = False) -> VideoReport:
    """Process a single video directory: detect + optionally transcode."""
    aweme_id = aweme_dir.name
    video_file = aweme_dir / "video.mp4"
    report = VideoReport(aweme_id=aweme_id)

    if not video_file.exists():
        report.error = "video.mp4 not found"
        return report

    # Step 1: Detect
    meta = detect_video(video_file)
    if meta is None:
        report.error = "ffprobe detection failed"
        return report
    report.meta = meta

    tiers = meta.available_tiers()
    print(f"  Source: {meta.width}x{meta.height} {meta.codec} "
          f"{meta.bitrate_kbps}kbps {meta.fps}fps {meta.duration_s}s")
    print(f"  Available tiers: {[f'{t}p' for t in tiers]}")

    if not transcode:
        # Dry-run: build quality_urls from what would be generated
        for tier in tiers:
            filename = "video.mp4" if tier == tiers[0] else f"video_{tier}p.mp4"
            report.quality_urls[f"{tier}p"] = f"videos/{aweme_id}/{filename}"
        return report

    # Step 2: Transcode lower tiers
    for i, tier in enumerate(tiers):
        if i == 0:
            # Source = highest tier, no transcode needed
            report.quality_urls[f"{tier}p"] = f"videos/{aweme_id}/video.mp4"
            continue

        result = transcode_video(video_file, tier, force)
        report.transcodes.append(result)
        if result.success:
            report.quality_urls[f"{tier}p"] = f"videos/{aweme_id}/{result.output_path.name}"

    # Step 3: Update JSONL
    if len(report.quality_urls) > 1:
        update_jsonl(aweme_id, report.quality_urls)

    return report


def print_summary(reports: list[VideoReport], elapsed: float):
    """Print a processing summary report."""
    total = len(reports)
    ok = sum(1 for r in reports if r.ok)
    multi_quality = sum(1 for r in reports if len(r.quality_urls) > 1)
    failed = total - ok

    print("\n" + "=" * 60)
    print("PROCESSING REPORT / 处理报告")
    print("=" * 60)
    print(f"  Total videos scanned:  {total}")
    print(f"  Detected successfully: {ok}")
    print(f"  Failed detection:      {failed}")
    print(f"  Multi-quality videos:  {multi_quality}")
    print(f"  Time elapsed:          {elapsed:.0f}s")
    print()

    # Per-video detail
    for r in reports:
        if not r.ok:
            print(f"  [{r.aweme_id}] FAIL: {r.error}")
            continue
        m = r.meta
        qualities = list(r.quality_urls.keys())
        trans_ok = sum(1 for t in r.transcodes if t.success)
        trans_fail = sum(1 for t in r.transcodes if not t.success)
        extra = f" | transcoded: {trans_ok} ok" if r.transcodes else ""
        if trans_fail:
            extra += f", {trans_fail} failed"
        print(f"  [{r.aweme_id}] {m.width}x{m.height} → {qualities}{extra}")

    # Transcode failures detail
    failures = [(r.aweme_id, t) for r in reports for t in r.transcodes if not t.success]
    if failures:
        print(f"\n  Transcode failures ({len(failures)}):")
        for aweme_id, t in failures:
            print(f"    [{aweme_id}] {t.height}p: {t.error[:100]}")


def main():
    parser = argparse.ArgumentParser(description="Video multi-quality preprocessing tool")
    parser.add_argument("--transcode", action="store_true", help="Actually transcode (default: dry-run)")
    parser.add_argument("--force", action="store_true", help="Overwrite existing transcoded files")
    args = parser.parse_args()

    if not VIDEO_DIR.exists():
        print(f"[ERROR] Video directory not found: {VIDEO_DIR}")
        print(f"  Configure VIDEO_DIR at the top of this script.")
        sys.exit(1)

    aweme_dirs = sorted(
        d for d in VIDEO_DIR.iterdir()
        if d.is_dir() and (d / "video.mp4").exists()
    )
    if not aweme_dirs:
        print(f"[ERROR] No video.mp4 files found under {VIDEO_DIR}")
        sys.exit(1)

    mode = "TRANSCODE" if args.transcode else "DRY-RUN (scan only)"
    print(f"Found {len(aweme_dirs)} videos. Mode: {mode}\n")

    start = time.time()
    reports = []
    for i, d in enumerate(aweme_dirs):
        print(f"[{i+1}/{len(aweme_dirs)}] {d.name}")
        report = process_video(d, transcode=args.transcode, force=args.force)
        reports.append(report)
        if not report.ok:
            print(f"  [SKIP] {report.error}")

    print_summary(reports, time.time() - start)


if __name__ == "__main__":
    main()
