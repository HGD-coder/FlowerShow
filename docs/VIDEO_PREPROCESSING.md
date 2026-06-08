# Video Preprocessing

FlowerShow uses `tools/preprocess_videos.py` to prepare local MP4 files for multi-quality playback.

## What It Does

- Uses `ffprobe` to read real display width/height, bitrate, codec, duration, FPS, and rotation metadata.
- Uses the source video's real short side as the highest selectable quality.
- Generates only lower qualities. It never upscales or labels a low-resolution source as HD.
- Handles portrait and landscape videos by scaling the display short side.
- Optionally generates `cover_360.jpg`.
- Atomically updates `quality_urls` in `video_data.jsonl` or JSON array metadata.
- Writes structured JSONL logs under `tools/logs/`.

## Quality Rules

Examples:

- Source `720x1280`: writes `720p`, `480p`, `360p`.
- Source `1080x1920`: writes `1080p`, `720p`, `480p`, `360p`.
- Source `480x854`: writes `480p`, `360p`.

For unusual source sizes, the exact real short side is kept as the top quality, for example `540p`, then standard lower tiers are generated.

## Commands

Dry-run only:

```powershell
python .\tools\preprocess_videos.py --limit 5
```

Generate MP4 qualities and update app metadata:

```powershell
python .\tools\preprocess_videos.py --transcode --retries 1
```

Overwrite existing generated files and also create local thumbnails:

```powershell
python .\tools\preprocess_videos.py --transcode --force --generate-covers
```

Use custom paths:

```powershell
python .\tools\preprocess_videos.py `
  --transcode `
  --video-dir D:\MediaCrawler\MediaCrawler\data\douyin\videos `
  --json-path D:\android-studio\flowershow\app\src\main\assets\video_data.jsonl
```

If Python is not on `PATH`, run with the bundled Codex Python used during validation:

```powershell
& 'C:\Users\12106\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\preprocess_videos.py --help
```

## Output Layout

For `videos/<aweme_id>/video.mp4`, generated files are:

```text
videos/<aweme_id>/video.mp4
videos/<aweme_id>/video_720p.mp4
videos/<aweme_id>/video_480p.mp4
videos/<aweme_id>/video_360p.mp4
videos/<aweme_id>/cover_360.jpg
```

The metadata entry is updated like this:

```json
{
  "aweme_id": "123",
  "quality_urls": {
    "1080p": "videos/123/video.mp4",
    "720p": "videos/123/video_720p.mp4",
    "480p": "videos/123/video_480p.mp4",
    "360p": "videos/123/video_360p.mp4"
  },
  "cover_thumbnail_url": "videos/123/cover_360.jpg"
}
```

The app reads `quality_urls` and `cover_thumbnail_url` through `AssetJsonLoader`. Feed and search thumbnails prefer `cover_thumbnail_url` and fall back to the original cover URL when no generated thumbnail exists.

App 会通过 `AssetJsonLoader` 读取 `quality_urls` 和 `cover_thumbnail_url`。Feed 与搜索缩略图会优先使用 `cover_thumbnail_url`，没有生成缩略图时再回退到原始封面 URL。

## Validation

The script validates each generated MP4 with `ffprobe` before replacing the final file. Metadata writes are atomic:

1. Write a temp metadata file.
2. Validate JSON/JSONL.
3. Copy a `.bak` backup.
4. Replace the original metadata file.

Failures are recorded in `tools/logs/preprocess_videos_*.jsonl`. Use `--fail-on-error` in CI or batch jobs when any failed video should stop the process.
