# Local Vector Search

FlowerShow uses a hybrid local search pipeline:

1. Lexical/fuzzy matching via `HybridSearchMatcher`.
2. Optional semantic vector search via ONNX Runtime.
3. Score fusion in `FakeVideoRepository`.

If vector assets are missing, the app automatically falls back to lexical search.

## Android Assets

Place these files under `app/src/main/assets/search/`:

- `model.onnx` - quantized ONNX feature extraction model from `Xenova/bge-small-zh-v1.5`, based on `BAAI/bge-small-zh-v1.5`.
- `vocab.txt` - WordPiece vocabulary used by the model.
- `video_embeddings.bin` - little-endian float32 vectors for videos.
- `video_embedding_manifest.json` - vector dimension and video id offsets.

Manifest shape:

```json
{
  "version": 1,
  "model": "Xenova/bge-small-zh-v1.5",
  "dimension": 512,
  "items": [
    { "id": "7565792429132107008", "offset": 0 }
  ]
}
```

Current generated asset sizes:

- `model.onnx`: about 24 MB
- `vocab.txt`: about 107 KB
- `video_embeddings.bin`: about 284 KB for 142 videos
- `video_embedding_manifest.json`: about 10 KB for 142 videos
- `video_content_embeddings.bin`: about 284 KB for 142 videos
- `video_content_searches.json`: about 126 KB for 142 videos
- `multimodal_search_suggestions.json`: about 8 KB

## Multimodal Video Content Assets

Video content vectors are generated offline from sampled video frames. The project uses
`Xenova/chinese-clip-vit-base-patch16` for preprocessing, but this model is about
191 MB, so it is intentionally kept under `build/ai-models/chinese-clip/` and is not
packaged into the APK.

The generated assets are:

- `video_content_embeddings.bin` - visual frame embedding averaged per video.
- `video_content_embedding_manifest.json` - visual embedding id/offset mapping.
- `video_content_searches.json` - ranked short search phrases per video.
- `multimodal_search_suggestions.json` - global ranked phrases used by "猜你想搜".

Runtime behavior:

- `相关搜索` uses the first phrase from `VideoItem.contentSearches` before falling back to text-only inference.
- `猜你想搜` mixes global multimodal suggestions before generic fallback suggestions.
- Search matching reads `contentSearches`.
- `generate_search_embeddings.py` also reads `video_content_searches.json`, so the smaller BGE text vector contains video-content-derived phrases.

Generate multimodal assets:

```powershell
python tools/generate_multimodal_search_assets.py `
  --input app/src/main/assets/video_data.jsonl `
  --video-root D:/MediaCrawler/MediaCrawler/data/douyin `
  --output app/src/main/assets/search `
  --model-dir build/ai-models/chinese-clip `
  --download-model
```

After multimodal assets change, regenerate the BGE search vectors:

```powershell
python tools/generate_search_embeddings.py `
  --input app/src/main/assets/video_data.jsonl `
  --output app/src/main/assets/search
```

## Generate Assets

Install Python dependencies:

```powershell
python -m pip install onnxruntime numpy
```

Download the ONNX model/tokenizer assets and generate video vectors:

```powershell
python tools/generate_search_embeddings.py `
  --input app/src/main/assets/video_data.jsonl `
  --output app/src/main/assets/search `
  --download-onnx-assets
```

Regenerate vectors only after the model/tokenizer assets already exist:

```powershell
python tools/generate_search_embeddings.py `
  --input app/src/main/assets/video_data.jsonl `
  --output app/src/main/assets/search
```

The script still supports `--backend sentence-transformers` and `--export-onnx` for
experiments, but the default project path is the lighter ONNX Runtime flow above.

## Runtime Flow

At query time:

1. `OnDeviceEmbeddingService` tokenizes the query with `vocab.txt`.
2. ONNX Runtime runs `model.onnx` and returns a normalized query embedding.
3. `AssetVectorSearchIndex` computes cosine similarity with local video vectors.
4. `FakeVideoRepository` fuses text score, vector score, and popularity score.

Fusion weights:

- Text score: `0.55`
- Vector score: `0.40`
- Popularity score: `0.05`

This keeps exact searches such as `虾` stable while improving semantic searches such as
`做饭`, `下班吃什么`, and `适合新手的菜`.
