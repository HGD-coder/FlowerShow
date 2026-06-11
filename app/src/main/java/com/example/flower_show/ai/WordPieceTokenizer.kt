package com.example.flower_show.ai

import android.content.Context

class WordPieceTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val maxTokenChars: Int = 100,
) {
    private val clsId = vocab["[CLS]"] ?: 101
    private val sepId = vocab["[SEP]"] ?: 102
    private val padId = vocab["[PAD]"] ?: 0
    private val unkId = vocab["[UNK]"] ?: 100

    data class Encoded(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray,
    )

    fun encode(text: String, maxLength: Int): Encoded {
        val tokenIds = mutableListOf<Int>()
        tokenIds += clsId
        for (token in basicTokenize(text)) {
            tokenIds += wordPiece(token)
            if (tokenIds.size >= maxLength - 1) break
        }
        tokenIds += sepId

        val inputIds = LongArray(maxLength) { padId.toLong() }
        val attentionMask = LongArray(maxLength)
        val tokenTypeIds = LongArray(maxLength)
        tokenIds.take(maxLength).forEachIndexed { index, id ->
            inputIds[index] = id.toLong()
            attentionMask[index] = 1L
        }
        return Encoded(inputIds, attentionMask, tokenTypeIds)
    }

    private fun basicTokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val builder = StringBuilder()

        fun flush() {
            if (builder.isNotEmpty()) {
                tokens += builder.toString().lowercase()
                builder.clear()
            }
        }

        text.forEach { char ->
            when {
                char.isWhitespace() || char.isSeparator() -> flush()
                char.isCjk() -> {
                    flush()
                    tokens += char.toString()
                }
                else -> builder.append(char)
            }
        }
        flush()
        return tokens
    }

    private fun wordPiece(token: String): List<Int> {
        if (token.length > maxTokenChars) return listOf(unkId)
        val pieces = mutableListOf<Int>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var current: String? = null
            while (start < end) {
                val sub = token.substring(start, end)
                val piece = if (start == 0) sub else "##$sub"
                if (vocab.containsKey(piece)) {
                    current = piece
                    break
                }
                end--
            }
            if (current == null) return listOf(unkId)
            pieces += vocab.getValue(current)
            start = end
        }
        return pieces
    }

    private fun Char.isSeparator(): Boolean {
        return this in setOf(
            '#', '，', ',', '。', '.', '！', '!', '？', '?', '、', '｜', '|',
            '/', '\\', '_', ':', ';', '；', '（', '）', '(', ')', '【', '】',
            '[', ']', '「', '」', '『', '』', '"', '\'', '~', '`',
        )
    }

    private fun Char.isCjk(): Boolean {
        return this in '\u4e00'..'\u9fff'
    }

    companion object {
        fun fromAssets(
            context: Context,
            assetPath: String = DEFAULT_VOCAB_ASSET,
        ): WordPieceTokenizer {
            val vocab = linkedMapOf<String, Int>()
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val token = line.trim()
                    if (token.isNotEmpty()) vocab[token] = index
                }
            }
            return WordPieceTokenizer(vocab)
        }

        const val DEFAULT_VOCAB_ASSET = "search/vocab.txt"
    }
}
