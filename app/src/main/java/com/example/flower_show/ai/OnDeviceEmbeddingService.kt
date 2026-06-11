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
            val options = OrtSession.SessionOptions()
            env.createSession(modelFile.absolutePath, options)
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
                val dim = shape[2].toInt()
                values.copyOfRange(0, dim)
            }
            shape.size >= 2 && shape[1] > 0L -> {
                val dim = shape[1].toInt()
                values.copyOfRange(0, minOf(dim, values.size))
            }
            else -> values
        }
    }

    private fun copyAssetToCache(assetPath: String): File {
        val outFile = File(context.cacheDir, assetPath.replace('/', '_'))
        if (outFile.exists() && outFile.length() > 0L) return outFile

        context.assets.open(assetPath).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile
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
