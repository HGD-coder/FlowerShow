#!/usr/bin/env python3
"""Generate video-content multimodal search assets for FlowerShow.

The model is intentionally used offline because Chinese CLIP is too large for the
current APK. Generated short phrases are then consumed by the app and by the
smaller on-device text embedding pipeline.
"""

from __future__ import annotations

import argparse
import json
import math
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Any

import numpy as np
import onnxruntime as ort
from PIL import Image


MODEL_REPO = "Xenova/chinese-clip-vit-base-patch16"
REMOTE_ONNX = "onnx/model_quantized.onnx"
MODEL_BASE_URL = "https://huggingface.co/{repo}/resolve/main/{path}"
DEFAULT_MODEL_DIR = Path("build/ai-models/chinese-clip")
DEFAULT_INPUT = Path("app/src/main/assets/video_data.jsonl")
DEFAULT_VIDEO_ROOT = Path("D:/MediaCrawler/MediaCrawler/data/douyin")
DEFAULT_OUTPUT = Path("app/src/main/assets/search")
DEFAULT_MAX_LENGTH = 64
DEFAULT_FRAME_COUNT = 3
DEFAULT_TOP_SEARCHES = 8

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

CURATED_CANDIDATES = [
    "家常菜", "美食教程", "下饭菜", "快手菜", "新手做饭", "懒人菜谱", "晚餐吃什么",
    "做饭教程", "家常小炒", "懒人快手菜", "好吃的菜", "厨房美食", "一人食",
    "虾滑做法", "脆皮虾", "鲜虾料理", "海鲜做法", "鸡肉做法", "土豆做法", "豆腐做法", "火锅做法",
    "甜品教程", "饮品制作", "早餐做法", "厨房技巧", "探店美食",
    "男生穿搭", "女生穿搭", "夏季穿搭", "通勤穿搭", "干净穿搭", "ootd穿搭",
    "旅行攻略", "旅行vlog", "城市打卡", "风景视频", "毕业旅行", "云南旅游",
    "汽车评测", "新车试驾", "SUV测评", "新能源车", "数码评测", "手机评测",
    "电脑配置", "桌面好物", "电子产品", "相机拍摄", "科技好物",
    "游戏实况", "单机游戏", "手游高光", "王者荣耀", "steam游戏", "游戏名场面",
    "健身教程", "减脂训练", "腹肌训练", "新手健身", "运动穿搭",
    "宠物日常", "猫咪搞笑", "萌宠视频", "狗狗日常",
    "音乐合集", "热门BGM", "耳机试听", "华语音乐",
    "影视剪辑", "剧情解说", "电影片单", "动漫名场面",
    "足球集锦", "篮球集锦", "梅西进球", "体育高光",
    "生活记录", "日常vlog", "治愈视频", "搞笑视频", "知识科普",
]

SEPARATORS = set("#，,、.。!！?？~|/\\_:;；（）()【】[]「」『』\"'`")
INTENT_HINTS = [
    "菜", "饭", "虾", "鸡", "鱼", "肉", "豆腐", "土豆", "青椒", "海鲜", "美食", "做法",
    "穿搭", "旅行", "旅游", "攻略", "汽车", "试驾", "评测", "手机", "数码", "游戏",
    "健身", "训练", "宠物", "猫", "狗", "音乐", "电影", "足球", "篮球",
]


def read_items(path: Path) -> list[dict[str, Any]]:
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return []
    if text.startswith("["):
        return json.loads(text)
    return [json.loads(line) for line in text.splitlines() if line.strip()]


def item_id(item: dict[str, Any]) -> str:
    return str(item.get("aweme_id") or item.get("id") or "").strip()


def hashtags(text: str) -> list[str]:
    import re

    return [match.strip() for match in re.findall(r"#([^#\s]+)", text) if match.strip()]


def clean_keyword(value: str, max_length: int = 16) -> str:
    value = value.strip()
    for char in "\n\r\t":
        value = value.replace(char, " ")
    value = " ".join(value.split())
    value = value.strip("# ，,、.。!！?？~|/\\_:;；（）()【】[]「」『』\"'`")
    if len(value) > max_length:
        value = value[:max_length]
    return value


def metadata_candidates(items: list[dict[str, Any]]) -> list[str]:
    candidates: list[str] = []
    for item in items:
        fields = [
            str(item.get("source_keyword") or ""),
            str(item.get("nickname") or item.get("author") or ""),
        ]
        title = str(item.get("title") or item.get("desc") or "")
        fields.extend(hashtags(title))
        for value in fields:
            keyword = clean_keyword(value)
            if 2 <= len(keyword) <= 16 and keyword not in candidates:
                candidates.append(keyword)
    return candidates


def item_candidates(item: dict[str, Any]) -> list[str]:
    title = str(item.get("title") or item.get("desc") or "")
    raw_values = [
        str(item.get("source_keyword") or ""),
        *hashtags(title),
    ]
    fragments: list[str] = []
    current: list[str] = []
    for char in title:
        if char.isspace() or char in SEPARATORS:
            if current:
                fragments.append("".join(current))
                current.clear()
        else:
            current.append(char)
    if current:
        fragments.append("".join(current))
    raw_values.extend(fragments)

    output: list[str] = []
    for value in raw_values:
        keyword = clean_keyword(value)
        if 2 <= len(keyword) <= 16 and keyword not in output:
            output.append(keyword)
    return output


def keyword_quality_bonus(keyword: str) -> float:
    return 0.04 if any(hint in keyword for hint in INTENT_HINTS) else 0.0


class WordPieceTokenizer:
    def __init__(self, vocab_path: Path, max_token_chars: int = 100) -> None:
        self.vocab: dict[str, int] = {}
        for index, line in enumerate(vocab_path.read_text(encoding="utf-8").splitlines()):
            token = line.strip()
            if token:
                self.vocab[token] = index
        self.max_token_chars = max_token_chars
        self.cls_id = self.vocab.get("[CLS]", 101)
        self.sep_id = self.vocab.get("[SEP]", 102)
        self.pad_id = self.vocab.get("[PAD]", 0)
        self.unk_id = self.vocab.get("[UNK]", 100)

    def encode(self, text: str, max_length: int) -> tuple[list[int], list[int]]:
        token_ids = [self.cls_id]
        for token in self.basic_tokenize(text):
            token_ids.extend(self.word_piece(token))
            if len(token_ids) >= max_length - 1:
                break
        token_ids.append(self.sep_id)

        input_ids = [self.pad_id] * max_length
        attention_mask = [0] * max_length
        for index, token_id in enumerate(token_ids[:max_length]):
            input_ids[index] = token_id
            attention_mask[index] = 1
        return input_ids, attention_mask

    def basic_tokenize(self, text: str) -> list[str]:
        tokens: list[str] = []
        builder: list[str] = []

        def flush() -> None:
            if builder:
                tokens.append("".join(builder).lower())
                builder.clear()

        for char in text:
            if char.isspace() or char in SEPARATORS:
                flush()
            elif "\u4e00" <= char <= "\u9fff":
                flush()
                tokens.append(char)
            else:
                builder.append(char)
        flush()
        return tokens

    def word_piece(self, token: str) -> list[int]:
        if len(token) > self.max_token_chars:
            return [self.unk_id]
        pieces: list[int] = []
        start = 0
        while start < len(token):
            end = len(token)
            current: str | None = None
            while start < end:
                sub = token[start:end]
                piece = sub if start == 0 else f"##{sub}"
                if piece in self.vocab:
                    current = piece
                    break
                end -= 1
            if current is None:
                return [self.unk_id]
            pieces.append(self.vocab[current])
            start = end
        return pieces


def normalize(rows: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(rows, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    return rows / norms


def download_file(repo: str, remote_path: str, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists() and output_path.stat().st_size > 0:
        return
    url = MODEL_BASE_URL.format(repo=repo, path=remote_path)
    print(f"Downloading {url} -> {output_path}")
    with urllib.request.urlopen(url) as response, output_path.open("wb") as output:
        shutil.copyfileobj(response, output)


def ensure_model(model_dir: Path, download: bool) -> None:
    if download:
        download_file(MODEL_REPO, REMOTE_ONNX, model_dir / "model_quantized.onnx")
        download_file(MODEL_REPO, "vocab.txt", model_dir / "vocab.txt")
        download_file(MODEL_REPO, "preprocessor_config.json", model_dir / "preprocessor_config.json")
    missing = [
        path for path in [
            model_dir / "model_quantized.onnx",
            model_dir / "vocab.txt",
            model_dir / "preprocessor_config.json",
        ]
        if not path.exists()
    ]
    if missing:
        raise SystemExit(
            "Missing Chinese CLIP assets. Re-run with --download-model. Missing:\n"
            + "\n".join(str(path) for path in missing)
        )


def video_path_for(item: dict[str, Any], video_root: Path) -> Path | None:
    video_id = item_id(item)
    quality_urls = item.get("quality_urls") or item.get("qualityUrls") or {}
    candidates: list[Path] = []
    if isinstance(quality_urls, dict):
        for name in ["360p", "480p", "720p", "1080p"]:
            value = quality_urls.get(name)
            if isinstance(value, str) and not value.startswith("http"):
                candidates.append(video_root / value)
    if video_id:
        candidates.extend([
            video_root / "videos" / video_id / "video_360p.mp4",
            video_root / "videos" / video_id / "video_480p.mp4",
            video_root / "videos" / video_id / "video.mp4",
        ])
    return next((path for path in candidates if path.exists()), None)


def ffprobe_duration(video_path: Path) -> float:
    command = [
        "ffprobe",
        "-v",
        "error",
        "-show_entries",
        "format=duration",
        "-of",
        "default=noprint_wrappers=1:nokey=1",
        str(video_path),
    ]
    result = subprocess.run(command, capture_output=True, text=True, check=True)
    return float(result.stdout.strip() or "0")


def sample_times(duration: float, count: int) -> list[float]:
    if duration <= 0:
        return [0.0]
    if count <= 1:
        return [min(duration * 0.5, max(duration - 0.1, 0.0))]
    points = [0.18, 0.50, 0.82][:count]
    return sorted({min(max(duration * point, 0.0), max(duration - 0.1, 0.0)) for point in points})


def extract_frames(video_path: Path, output_dir: Path, count: int) -> list[Path]:
    duration = ffprobe_duration(video_path)
    frames: list[Path] = []
    for index, timestamp in enumerate(sample_times(duration, count)):
        output = output_dir / f"frame_{index}.jpg"
        command = [
            "ffmpeg",
            "-y",
            "-v",
            "error",
            "-ss",
            f"{timestamp:.3f}",
            "-i",
            str(video_path),
            "-frames:v",
            "1",
            str(output),
        ]
        subprocess.run(command, check=True)
        if output.exists() and output.stat().st_size > 0:
            frames.append(output)
    return frames


def load_preprocessor(model_dir: Path) -> tuple[list[float], list[float], int, int]:
    config = json.loads((model_dir / "preprocessor_config.json").read_text(encoding="utf-8"))
    mean = [float(value) for value in config.get("image_mean", [0.48145466, 0.4578275, 0.40821073])]
    std = [float(value) for value in config.get("image_std", [0.26862954, 0.26130258, 0.27577711])]
    size = config.get("size", {})
    height = int(size.get("height", 224))
    width = int(size.get("width", 224))
    return mean, std, height, width


def image_tensor(path: Path, mean: list[float], std: list[float], height: int, width: int) -> np.ndarray:
    image = Image.open(path).convert("RGB").resize((width, height), Image.Resampling.BICUBIC)
    array = np.asarray(image).astype(np.float32) / 255.0
    array = (array - np.asarray(mean, dtype=np.float32)) / np.asarray(std, dtype=np.float32)
    return np.transpose(array, (2, 0, 1))


class ChineseClipEncoder:
    def __init__(self, model_dir: Path, max_length: int) -> None:
        self.session = ort.InferenceSession(str(model_dir / "model_quantized.onnx"), providers=["CPUExecutionProvider"])
        self.tokenizer = WordPieceTokenizer(model_dir / "vocab.txt")
        self.mean, self.std, self.height, self.width = load_preprocessor(model_dir)
        self.max_length = max_length
        self.dummy_image = np.zeros((1, 3, self.height, self.width), dtype=np.float32)
        input_ids, attention_mask = self.tokenizer.encode("", max_length)
        self.dummy_text = {
            "input_ids": np.asarray([input_ids], dtype=np.int64),
            "attention_mask": np.asarray([attention_mask], dtype=np.int64),
        }

    def encode_texts(self, texts: list[str], batch_size: int) -> np.ndarray:
        vectors: list[np.ndarray] = []
        for start in range(0, len(texts), batch_size):
            batch = texts[start:start + batch_size]
            encoded = [self.tokenizer.encode(text, self.max_length) for text in batch]
            inputs = {
                "input_ids": np.asarray([item[0] for item in encoded], dtype=np.int64),
                "attention_mask": np.asarray([item[1] for item in encoded], dtype=np.int64),
                "pixel_values": self.dummy_image,
            }
            text_embeds = self.session.run(["text_embeds"], inputs)[0]
            vectors.append(text_embeds.astype(np.float32))
        return normalize(np.vstack(vectors)).astype(np.float32)

    def encode_images(self, frame_paths: list[Path], batch_size: int) -> np.ndarray:
        vectors: list[np.ndarray] = []
        for start in range(0, len(frame_paths), batch_size):
            batch = frame_paths[start:start + batch_size]
            pixels = np.asarray(
                [image_tensor(path, self.mean, self.std, self.height, self.width) for path in batch],
                dtype=np.float32,
            )
            inputs = {
                "pixel_values": pixels,
                "input_ids": self.dummy_text["input_ids"],
                "attention_mask": self.dummy_text["attention_mask"],
            }
            image_embeds = self.session.run(["image_embeds"], inputs)[0]
            vectors.append(image_embeds.astype(np.float32))
        return normalize(np.vstack(vectors)).astype(np.float32)


def write_vectors(output_dir: Path, ids: list[str], vectors: list[np.ndarray], model: str) -> None:
    if not ids:
        return
    output_dir.mkdir(parents=True, exist_ok=True)
    dimension = int(vectors[0].shape[0])
    manifest = {
        "version": 1,
        "model": model,
        "dimension": dimension,
        "items": [{"id": video_id, "offset": index} for index, video_id in enumerate(ids)],
    }
    (output_dir / "video_content_embedding_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    with (output_dir / "video_content_embeddings.bin").open("wb") as file:
        for vector in vectors:
            file.write(struct.pack("<" + "f" * dimension, *[float(value) for value in vector]))


def generate_assets(args: argparse.Namespace) -> None:
    ensure_model(args.model_dir, args.download_model)
    items = read_items(args.input)
    unique_items: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in items:
        video_id = item_id(item)
        if not video_id or video_id in seen:
            continue
        seen.add(video_id)
        unique_items.append(item)
    if args.limit:
        unique_items = unique_items[:args.limit]

    candidates = list(dict.fromkeys(CURATED_CANDIDATES))
    if args.include_metadata_candidates:
        candidates = list(dict.fromkeys(candidates + metadata_candidates(items)))
    encoder = ChineseClipEncoder(args.model_dir, args.max_length)
    candidate_prompts = [f"一张关于{keyword}的图片" for keyword in candidates]
    candidate_vectors = encoder.encode_texts(candidate_prompts, args.batch_size)

    vector_ids: list[str] = []
    video_vectors: list[np.ndarray] = []
    content_items: list[dict[str, Any]] = []
    global_scores: dict[str, float] = defaultdict(float)
    global_counts: dict[str, int] = defaultdict(int)

    with tempfile.TemporaryDirectory(prefix="flowershow_frames_") as temp_name:
        temp_root = Path(temp_name)
        for index, item in enumerate(unique_items, start=1):
            video_id = item_id(item)
            path = video_path_for(item, args.video_root)
            if path is None:
                print(f"[{index}/{len(unique_items)}] skip missing video {video_id}")
                continue
            frame_dir = temp_root / video_id
            frame_dir.mkdir(parents=True, exist_ok=True)
            try:
                frames = extract_frames(path, frame_dir, args.frames)
                if not frames:
                    print(f"[{index}/{len(unique_items)}] skip no frames {video_id}")
                    continue
                frame_vectors = encoder.encode_images(frames, args.batch_size)
            except Exception as exc:
                print(f"[{index}/{len(unique_items)}] failed {video_id}: {exc}")
                continue

            vector = normalize(frame_vectors.mean(axis=0, keepdims=True))[0].astype(np.float32)
            local_candidates = item_candidates(item)
            if local_candidates:
                local_prompts = [f"一张关于{keyword}的图片" for keyword in local_candidates]
                local_vectors = encoder.encode_texts(local_prompts, args.batch_size)
                scoring_candidates = local_candidates + candidates
                scoring_vectors = np.vstack([local_vectors, candidate_vectors])
            else:
                scoring_candidates = candidates
                scoring_vectors = candidate_vectors
            scores = scoring_vectors @ vector
            if local_candidates and args.local_candidate_boost > 0:
                scores[:len(local_candidates)] += args.local_candidate_boost
            quality_bonus = np.asarray(
                [keyword_quality_bonus(keyword) for keyword in scoring_candidates],
                dtype=np.float32,
            )
            scores = scores + quality_bonus
            order = np.argsort(scores)[::-1]
            local_count = len(local_candidates)
            local_order = [int(candidate_index) for candidate_index in order if int(candidate_index) < local_count]
            global_order = [
                int(candidate_index)
                for candidate_index in order
                if int(candidate_index) >= local_count and scores[int(candidate_index)] >= args.global_candidate_min_score
            ]
            ranked_indices = local_order + global_order
            searches: list[dict[str, Any]] = []
            for candidate_index in ranked_indices:
                keyword = scoring_candidates[int(candidate_index)]
                score = float(scores[int(candidate_index)])
                if len(keyword) < 2:
                    continue
                if any(keyword == existing["keyword"] for existing in searches):
                    continue
                searches.append({"keyword": keyword, "score": round(score, 6)})
                global_scores[keyword] += max(score, 0.0)
                global_counts[keyword] += 1
                if len(searches) >= args.top_searches:
                    break

            vector_ids.append(video_id)
            video_vectors.append(vector)
            content_items.append({
                "id": video_id,
                "source": str(path.relative_to(args.video_root)) if path.is_relative_to(args.video_root) else str(path),
                "frameCount": len(frames),
                "searches": searches,
            })
            print(f"[{index}/{len(unique_items)}] {video_id}: {', '.join(s['keyword'] for s in searches[:3])}")

    args.output.mkdir(parents=True, exist_ok=True)
    write_vectors(args.output, vector_ids, video_vectors, MODEL_REPO)
    (args.output / "video_content_searches.json").write_text(
        json.dumps(
            {
                "version": 1,
                "model": MODEL_REPO,
                "candidateCount": len(candidates),
                "items": content_items,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    ranked_global = sorted(
        global_scores,
        key=lambda keyword: global_scores[keyword] + math.log1p(global_counts[keyword]) * 0.05,
        reverse=True,
    )
    suggestions = [
        {
            "keyword": keyword,
            "score": round(global_scores[keyword], 6),
            "videoCount": global_counts[keyword],
        }
        for keyword in ranked_global[: args.global_count]
    ]
    (args.output / "multimodal_search_suggestions.json").write_text(
        json.dumps(
            {
                "version": 1,
                "model": MODEL_REPO,
                "suggestions": suggestions,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"Wrote {len(content_items)} multimodal video search items to {args.output}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--video-root", type=Path, default=DEFAULT_VIDEO_ROOT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--model-dir", type=Path, default=DEFAULT_MODEL_DIR)
    parser.add_argument("--download-model", action="store_true")
    parser.add_argument("--frames", type=int, default=DEFAULT_FRAME_COUNT)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--max-length", type=int, default=DEFAULT_MAX_LENGTH)
    parser.add_argument("--top-searches", type=int, default=DEFAULT_TOP_SEARCHES)
    parser.add_argument("--global-count", type=int, default=80)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--include-metadata-candidates", action="store_true")
    parser.add_argument("--local-candidate-boost", type=float, default=0.08)
    parser.add_argument("--global-candidate-min-score", type=float, default=0.48)
    args = parser.parse_args()
    generate_assets(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
