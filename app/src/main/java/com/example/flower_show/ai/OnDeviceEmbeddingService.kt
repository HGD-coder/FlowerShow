package com.example.flower_show.ai

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.LongBuffer

class OnDeviceEmbeddingService private constructor(
    private val context: Context,
    private val modelAssetPath: String,
    private val tokenizer: WordPieceTokenizer,
    private val maxLength: Int,
) {
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val session: OrtSession? by lazy { createSessionOrNull() }

    fun embed(text: String): FloatArray? {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return null
        val activeSession = session ?: return null
        val encoded = tokenizer.encode(cleanText, maxLength)
        val shape = longArrayOf(1L, maxLength.toLong())
        val tensors = mutableListOf<OnnxTensor>()

        return try {
            val inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.inputIds), shape)
            val attentionMask = OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.attentionMask), shape)
            tensors += inputIds
            tensors += attentionMask

            val inputs = linkedMapOf<String, OnnxTensor>()
            val inputNames = activeSession.inputNames
            inputs["input_ids"] = inputIds
            inputs["attention_mask"] = attentionMask
            if ("token_type_ids" in inputNames) {
                val tokenTypeIds = OnnxTensor.createTensor(env, LongBuffer.wrap(encoded.tokenTypeIds), shape)
                tensors += tokenTypeIds
                inputs["token_type_ids"] = tokenTypeIds
            }

            activeSession.run(inputs).use { result ->
                val output = result.get(0) as? OnnxTensor ?: return null
                EmbeddingVectorUtils.normalize(extractEmbedding(output))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Embedding inference failed; falling back to lexical search.", e)
            null
        } finally {
            tensors.forEach { it.close() }
        }
    }

    private fun createSessionOrNull(): OrtSession? {
        return try {
            val modelFile = copyAssetToCache(modelAssetPath)
            // SessionOptions 持有原生资源，必须关闭。
            OrtSession.SessionOptions().use { options ->
                env.createSession(modelFile.absolutePath, options)
            }
        } catch (e: Exception) {
            Log.i(TAG, "Embedding model is not available yet: $modelAssetPath")
            null
        }
    }

    private fun extractEmbedding(tensor: OnnxTensor): FloatArray {
        val shape = tensor.info.shape
        val buffer = tensor.floatBuffer
        buffer.rewind()
        val values = FloatArray(buffer.remaining())
        buffer.get(values)

        return when {
            shape.size >= 3 && shape[2] > 0L -> {
                // shape[2] 超 Int 范围时 toInt() 溢出为负，先钳制再 minOf，
                // 避免 copyOfRange 抛异常后被外层 catch 吞掉、向量搜索静默禁用。
                val dim = shape[2].toInt().coerceAtLeast(0)
                values.copyOfRange(0, minOf(dim, values.size))
            }
            shape.size >= 2 && shape[1] > 0L -> {
                val dim = shape[1].toInt().coerceAtLeast(0)
                values.copyOfRange(0, minOf(dim, values.size))
            }
            else -> values
        }
    }

    private fun copyAssetToCache(assetPath: String): File {
        context.assets.open(assetPath).use { input ->
            // available() 对 APK 资产流返回剩余（未压缩）字节数，作为资产长度指纹。
            val assetLength = input.available().toLong()
            val baseName = assetPath.replace('/', '_')
            // 把资产长度编进缓存文件名：cacheDir 会跨应用升级保留，
            // 只判断"文件存在且非空"会让更新后打包的新模型永远不生效。
            val outFile = File(context.cacheDir, "$baseName.$assetLength")
            if (outFile.exists() && outFile.length() == assetLength) return outFile

            // 先写临时文件再原子重命名：拷贝中途进程被杀不会留下
            // 非空但截断的缓存被后续启动当作有效模型加载。
            val tmpFile = File(context.cacheDir, "$baseName.$assetLength.tmp")
            try {
                tmpFile.outputStream().use { output -> input.copyTo(output) }
                // 清掉旧版本（含旧命名方案）的缓存副本，避免 cacheDir 积累多个模型文件。
                context.cacheDir.listFiles()?.forEach { cached ->
                    if (cached.isFile && cached.name.startsWith(baseName) && cached != outFile) {
                        cached.delete()
                    }
                }
                if (outFile.exists()) outFile.delete()
                check(tmpFile.renameTo(outFile)) { "Failed to move model cache into place" }
            } finally {
                tmpFile.delete()
            }
            return outFile
        }
    }

    companion object {
        private const val TAG = "OnDeviceEmbedding"
        private const val DEFAULT_MODEL_ASSET = "search/model.onnx"
        private const val DEFAULT_MAX_LENGTH = 64

        fun createOrNull(
            context: Context,
            modelAssetPath: String = DEFAULT_MODEL_ASSET,
            vocabAssetPath: String = WordPieceTokenizer.DEFAULT_VOCAB_ASSET,
            maxLength: Int = DEFAULT_MAX_LENGTH,
        ): OnDeviceEmbeddingService? {
            return try {
                OnDeviceEmbeddingService(
                    context = context.applicationContext,
                    modelAssetPath = modelAssetPath,
                    tokenizer = WordPieceTokenizer.fromAssets(context, vocabAssetPath),
                    maxLength = maxLength,
                )
            } catch (e: Exception) {
                Log.i(TAG, "Embedding tokenizer is not available yet: $vocabAssetPath")
                null
            }
        }
    }
}
