"""
Video quality detector & transcoder / 视频画质检测 + 转码脚本

1. Scans video files, detects original resolution via ffprobe
2. Generates 2 lower-res versions (one step down, minimum 360p)
3. Updates JSONL with qualityUrls mapping matching AssetJsonLoader URL pattern

Usage:
  python generate_qualities.py                    # dry-run: detect only
  python generate_qualities.py --transcode        # detect + transcode + update JSONL
"""

import json, os, subprocess, sys, argparse
from pathlib import Path

# Config / 配置
VIDEO_DIR = Path(r"D:\MediaCrawler\MediaCrawler\data\douyin\videos")
JSONL_PATH = Path(r"D:\android-studio\flowershow\app\src\main\assets\video_data.jsonl")

# Quality tiers: heights we can generate (must be <= original)
QUALITY_TIERS = [2160, 1440, 1080, 720, 480, 360]


def detect_resolution(video_path: Path) -> int | None:
    """Detect video height via ffprobe / 用 ffprobe 检测视频高度"""
    try:
        result = subprocess.run(
            ["ffprobe", "-v", "error", "-select_streams", "v:0",
             "-show_entries", "stream=height", "-of", "csv=p=0",
             str(video_path)],
            capture_output=True, text=True, timeout=30,
        )
        height = int(result.stdout.strip())
        return height
    except Exception as e:
        print(f"  [WARN] ffprobe failed: {e}")
        return None


def select_quality_tiers(original_height: int) -> list[int]:
    """Select tiers <= original height, at most 3 (original + 2 lower) / 最多3档"""
    available = [t for t in QUALITY_TIERS if t <= original_height]
    # Take highest 3 (original is highest)
    return sorted(available, reverse=True)[:3]


def transcode_to(video_path: Path, target_height: int) -> Path | None:
    """Transcode video to target height / 转码到目标高度"""
    output_path = video_path.parent / f"video_{target_height}p.mp4"
    if output_path.exists():
        print(f"    [SKIP] {output_path.name} already exists")
        return output_path

    # Bitrate roughly proportional to height
    bitrate = int(target_height * 1.5)  # kbps
    print(f"    Transcoding {video_path.name} → {target_height}p ({bitrate}kbps)...")

    result = subprocess.run([
        "ffmpeg", "-y", "-i", str(video_path),
        "-vf", f"scale=-1:{target_height}",
        "-b:v", f"{bitrate}k",
        "-c:a", "aac", "-b:a", "128k",
        "-movflags", "+faststart",
        str(output_path),
    ], capture_output=True, text=True, timeout=300)

    if result.returncode == 0:
        print(f"    [OK] {output_path.name}")
        return output_path
    else:
        print(f"    [FAIL] {result.stderr[-200:]}")
        return None


def update_jsonl(aweme_id: str, quality_map: dict[str, str]):
    """Update JSONL entry with qualityUrls / 更新 JSONL 中的 qualityUrls"""
    if not JSONL_PATH.exists():
        print(f"[WARN] JSONL not found: {JSONL_PATH}")
        return

    lines = JSONL_PATH.read_text(encoding="utf-8").strip().split("\n")
    updated = []
    for line in lines:
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            updated.append(line)
            continue

        if entry.get("aweme_id") == aweme_id:
            entry["quality_urls"] = quality_map
            print(f"  [JSONL] Updated {aweme_id}: {quality_map}")

        updated.append(json.dumps(entry, ensure_ascii=False))

    JSONL_PATH.write_text("\n".join(updated) + "\n", encoding="utf-8")
    print(f"  [JSONL] Written to {JSONL_PATH}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--transcode", action="store_true", help="Actually transcode videos")
    args = parser.parse_args()

    if not VIDEO_DIR.exists():
        print(f"[ERROR] Video dir not found: {VIDEO_DIR}")
        sys.exit(1)

    aweme_dirs = sorted(VIDEO_DIR.iterdir())
    print(f"Found {len(aweme_dirs)} video directories\n")

    for d in aweme_dirs:
        if not d.is_dir():
            continue
        aweme_id = d.name
        video_file = d / "video.mp4"
        if not video_file.exists():
            print(f"[SKIP] {aweme_id}: no video.mp4")
            continue

        print(f"[{aweme_id}]")
        height = detect_resolution(video_file)
        if height is None:
            continue

        tiers = select_quality_tiers(height)
        original_tier = tiers[0]  # highest = original
        print(f"  Original: {height}p → tiers: {[f'{t}p' for t in tiers]}")

        if not args.transcode:
            continue

        quality_map = {}
        # Keep original as the highest tier
        quality_map[f"{original_tier}p"] = f"videos/{aweme_id}/video.mp4"

        # Generate lower tiers
        for tier in tiers[1:]:
            output = transcode_to(video_file, tier)
            if output:
                filename = output.name  # e.g. video_480p.mp4
                quality_map[f"{tier}p"] = f"videos/{aweme_id}/{filename}"

        if len(quality_map) > 1:
            update_jsonl(aweme_id, quality_map)

    print("\nDone.")

if __name__ == "__main__":
    main()
