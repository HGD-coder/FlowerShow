#!/usr/bin/env python3
"""Generate local semantic-search assets for FlowerShow.

Default output:
  app/src/main/assets/search/video_embeddings.bin
  app/src/main/assets/search/video_embedding_manifest.json

Optional model export:
  app/src/main/assets/search/model.onnx
  app/src/main/assets/search/vocab.txt
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import struct
import subprocess
import sys
import urllib.request
from pathlib import Path
from typing import Any


DEFAULT_MODEL = "Xenova/bge-small-zh-v1.5"
DEFAULT_REMOTE_ONNX = "onnx/model_quantized.onnx"
DEFAULT_MAX_LENGTH = 64
DOWNLOAD_BASE_URL = "https://huggingface.co/{repo}/resolve/main/{path}"


def read_items(path: Path) -> list[dict[str, Any]]:
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return []
    if text.startswith("["):
        return json.loads(text)
    return [json.loads(line) for line in text.splitlines() if line.strip()]


def hashtags(text: str) -> list[str]:
    return [match.strip() for match in re.findall(r"#([^#\s]+)", text) if match.strip()]


def search_text(item: dict[str, Any]) -> str:
    title = str(item.get("title") or item.get("desc") or "")
    desc = str(item.get("desc") or "")
    source_keyword = str(item.get("source_keyword") or "")
    nickname = str(item.get("nickname") or item.get("author") or "")
    tags = hashtags(title + " " + desc)
    parts = [title, desc, source_keyword, nickname, *tags]
    return " ".join(part for part in parts if part).strip()


def item_id(item: dict[str, Any]) -> str:
    return str(item.get("aweme_id") or item.get("id") or "").strip()


def read_content_searches(path: Path | None) -> dict[str, list[str]]:
    if path is None or not path.exists():
        return {}
    root = json.loads(path.read_text(encoding="utf-8"))
    output: dict[str, list[str]] = {}
    for item in root.get("items", []):
        video_id = str(item.get("id") or "").strip()
        if not video_id:
            continue
        searches: list[str] = []
        for value in item.get("searches", []):
            if isinstance(value, str):
                keyword = value
            elif isinstance(value, dict):
                keyword = str(value.get("keyword") or "")
            else:
                keyword = ""
            keyword = keyword.strip()
            if len(keyword) >= 2 and keyword not in searches:
                searches.append(keyword)
        if searches:
            output[video_id] = searches
    return output


def item_content_searches(item: dict[str, Any], sidecar: dict[str, list[str]]) -> list[str]:
    video_id = item_id(item)
    raw = item.get("content_searches") or item.get("contentSearches")
    values = raw if isinstance(raw, list) else []
    searches: list[str] = []
    for value in values:
        if isinstance(value, str):
            keyword = value
        elif isinstance(value, dict):
            keyword = str(value.get("keyword") or "")
        else:
            keyword = ""
        keyword = keyword.strip()
        if len(keyword) >= 2 and keyword not in searches:
            searches.append(keyword)
    for keyword in sidecar.get(video_id, []):
        if keyword not in searches:
            searches.append(keyword)
    return searches


def write_vectors(
    output_dir: Path,
    model_name: str,
    ids: list[str],
    embeddings: Any,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    if len(ids) == 0:
        raise ValueError("No videos with valid ids were found.")

    dimension = len(embeddings[0])
    manifest = {
        "version": 1,
        "model": model_name,
        "dimension": dimension,
        "items": [
            {"id": video_id, "offset": index}
            for index, video_id in enumerate(ids)
        ],
    }

    (output_dir / "video_embedding_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    with (output_dir / "video_embeddings.bin").open("wb") as file:
        for vector in embeddings:
            file.write(struct.pack("<" + "f" * dimension, *[float(v) for v in vector]))


def prepare_search_texts(
    input_path: Path,
    content_searches_path: Path | None = None,
) -> tuple[list[str], list[str]]:
    items = read_items(input_path)
    content_searches = read_content_searches(content_searches_path)
    seen_ids: set[str] = set()
    ids: list[str] = []
    texts: list[str] = []
    for item in items:
        video_id = item_id(item)
        if not video_id or video_id in seen_ids:
            continue
        text = search_text(item)
        visual_terms = item_content_searches(item, content_searches)
        if visual_terms:
            text = " ".join([text, *visual_terms]).strip()
        if not text:
            continue
        seen_ids.add(video_id)
        ids.append(video_id)
        texts.append(text)
    return ids, texts


def generate_embeddings_sentence_transformers(
    input_path: Path,
    output_dir: Path,
    model_name: str,
    batch_size: int,
    content_searches_path: Path | None,
) -> None:
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError as exc:
        raise SystemExit(
            "Missing dependency. Install with:\n"
            "  pip install sentence-transformers torch transformers"
        ) from exc

    ids, texts = prepare_search_texts(input_path, content_searches_path)

    model = SentenceTransformer(model_name)
    embeddings = model.encode(
        texts,
        batch_size=batch_size,
        normalize_embeddings=True,
        show_progress_bar=True,
    )
    write_vectors(output_dir, model_name, ids, embeddings)
    print(f"Wrote {len(ids)} vectors to {output_dir}")


class WordPieceTokenizer:
    def __init__(self, vocab_path: Path, max_token_chars: int = 100) -> None:
        self.vocab = {
            token.strip(): index
            for index, token in enumerate(vocab_path.read_text(encoding="utf-8").splitlines())
            if token.strip()
        }
        self.max_token_chars = max_token_chars
        self.cls_id = self.vocab.get("[CLS]", 101)
        self.sep_id = self.vocab.get("[SEP]", 102)
        self.pad_id = self.vocab.get("[PAD]", 0)
        self.unk_id = self.vocab.get("[UNK]", 100)

    def encode(self, text: str, max_length: int) -> tuple[list[int], list[int], list[int]]:
        token_ids = [self.cls_id]
        for token in self.basic_tokenize(text):
            token_ids.extend(self.word_piece(token))
            if len(token_ids) >= max_length - 1:
                break
        token_ids.append(self.sep_id)

        input_ids = [self.pad_id] * max_length
        attention_mask = [0] * max_length
        token_type_ids = [0] * max_length
        for index, token_id in enumerate(token_ids[:max_length]):
            input_ids[index] = token_id
            attention_mask[index] = 1
        return input_ids, attention_mask, token_type_ids

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


SEPARATORS = set("#，,、.。!！?？~|/\\_:;；（）()【】[]「」『』\"'`")


def l2_normalize(rows: Any) -> Any:
    import numpy as np

    norms = np.linalg.norm(rows, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    return rows / norms


def generate_embeddings_onnx(
    input_path: Path,
    output_dir: Path,
    model_name: str,
    batch_size: int,
    max_length: int,
    content_searches_path: Path | None,
) -> None:
    try:
        import numpy as np
        import onnxruntime as ort
    except ImportError as exc:
        raise SystemExit(
            "Missing dependency. Install with:\n"
            "  pip install onnxruntime numpy"
        ) from exc

    model_path = output_dir / "model.onnx"
    vocab_path = output_dir / "vocab.txt"
    if not model_path.exists() or not vocab_path.exists():
        raise SystemExit(
            "Missing ONNX assets. Run again with --download-onnx-assets, "
            "or place model.onnx and vocab.txt under the output directory."
        )

    ids, texts = prepare_search_texts(input_path, content_searches_path)
    tokenizer = WordPieceTokenizer(vocab_path)
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    input_names = {item.name for item in session.get_inputs()}

    embeddings: list[Any] = []
    for start in range(0, len(texts), batch_size):
        batch = texts[start:start + batch_size]
        encoded = [tokenizer.encode(text, max_length) for text in batch]
        input_ids = np.asarray([item[0] for item in encoded], dtype=np.int64)
        attention_mask = np.asarray([item[1] for item in encoded], dtype=np.int64)
        inputs: dict[str, Any] = {
            "input_ids": input_ids,
            "attention_mask": attention_mask,
        }
        if "token_type_ids" in input_names:
            inputs["token_type_ids"] = np.asarray([item[2] for item in encoded], dtype=np.int64)

        output = session.run(None, inputs)[0]
        if output.ndim == 3:
            pooled = output[:, 0, :]
        elif output.ndim == 2:
            pooled = output
        else:
            raise ValueError(f"Unexpected model output shape: {output.shape}")
        embeddings.extend(l2_normalize(pooled).astype(np.float32))
        print(f"Encoded {min(start + batch_size, len(texts))}/{len(texts)}")

    write_vectors(output_dir, model_name, ids, embeddings)
    print(f"Wrote {len(ids)} vectors to {output_dir}")


def download_huggingface_file(repo: str, remote_path: str, target_path: Path) -> None:
    target_path.parent.mkdir(parents=True, exist_ok=True)
    url = DOWNLOAD_BASE_URL.format(repo=repo, path=remote_path)
    print(f"Downloading {url} -> {target_path}")
    with urllib.request.urlopen(url) as response, target_path.open("wb") as output:
        shutil.copyfileobj(response, output)


def download_onnx_assets(output_dir: Path, model_name: str, remote_onnx: str) -> None:
    download_huggingface_file(model_name, remote_onnx, output_dir / "model.onnx")
    download_huggingface_file(model_name, "vocab.txt", output_dir / "vocab.txt")
    print(f"Wrote ONNX model assets to {output_dir}")


def export_onnx_model(model_name: str, output_dir: Path) -> None:
    export_dir = output_dir / "_onnx_export"
    if export_dir.exists():
        shutil.rmtree(export_dir)
    export_dir.mkdir(parents=True, exist_ok=True)

    command = [
        "optimum-cli",
        "export",
        "onnx",
        "--model",
        model_name,
        "--task",
        "feature-extraction",
        str(export_dir),
    ]
    try:
        subprocess.run(command, check=True)
    except FileNotFoundError as exc:
        raise SystemExit(
            "Missing optimum-cli. Install with:\n"
            "  pip install 'optimum[onnxruntime]'"
        ) from exc

    onnx_files = sorted(export_dir.glob("*.onnx"))
    if not onnx_files:
        raise SystemExit(f"No .onnx file was exported under {export_dir}")
    shutil.copy2(onnx_files[0], output_dir / "model.onnx")

    vocab_candidates = list(export_dir.rglob("vocab.txt"))
    if vocab_candidates:
        shutil.copy2(vocab_candidates[0], output_dir / "vocab.txt")
    print(f"Wrote ONNX model assets to {output_dir}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("app/src/main/assets/video_data.jsonl"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/search"),
    )
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument(
        "--content-searches",
        type=Path,
        default=Path("app/src/main/assets/search/video_content_searches.json"),
    )
    parser.add_argument(
        "--backend",
        choices=["onnx", "sentence-transformers"],
        default="onnx",
    )
    parser.add_argument("--max-length", type=int, default=DEFAULT_MAX_LENGTH)
    parser.add_argument("--download-onnx-assets", action="store_true")
    parser.add_argument("--remote-onnx", default=DEFAULT_REMOTE_ONNX)
    parser.add_argument("--export-onnx", action="store_true")
    args = parser.parse_args()

    if args.download_onnx_assets:
        download_onnx_assets(args.output, args.model, args.remote_onnx)
    if args.backend == "onnx":
        generate_embeddings_onnx(
            input_path=args.input,
            output_dir=args.output,
            model_name=args.model,
            batch_size=args.batch_size,
            max_length=args.max_length,
            content_searches_path=args.content_searches,
        )
    else:
        generate_embeddings_sentence_transformers(
            args.input,
            args.output,
            args.model,
            args.batch_size,
            args.content_searches,
        )
    if args.export_onnx:
        export_onnx_model(args.model, args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
