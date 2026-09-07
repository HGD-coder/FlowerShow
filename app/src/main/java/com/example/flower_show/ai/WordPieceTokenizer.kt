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
            val pieces = wordPiece(token)
            // 追加前检查：为 [SEP] 预留位置。若一个 token 被拆成多段直接越过
            // maxLength - 1，[SEP] 会被 take(maxLength) 截掉，模型输入缺分隔符。
            if (tokenIds.size + pieces.size > maxLength - 1) break
            tokenIds += pieces
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

        // 按 code point 迭代：按 Char(UTF-16 code unit) 迭代会把 emoji 等
        // 增补平面字符拆成孤立代理项，几乎必然命中 UNK。
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            when {
                Character.isWhitespace(codePoint) || codePoint in SEPARATOR_CODE_POINTS -> flush()
                codePoint in CJK_START..CJK_END -> {
                    flush()
                    tokens += String(Character.toChars(codePoint))
                }
                else -> builder.appendCodePoint(codePoint)
            }
            index += charCount
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
            if (current == null) {
                // 与 BERT 参考实现一致：词内任何一段匹配失败时，整个词降级为
                // 单个 [UNK]。模型训练数据里不存在"前缀片段 + 词中 [UNK]"的序列，
                // 保留已匹配前缀反而是分布外输入，会扭曲嵌入向量。
                return listOf(unkId)
            }
            pieces += vocab.getValue(current)
            start = end
        }
        return pieces
    }

    companion object {
        private const val CJK_START = 0x4E00
        private const val CJK_END = 0x9FFF

        private val SEPARATOR_CODE_POINTS = setOf(
            '#'.code, '，'.code, ','.code, '。'.code, '.'.code, '！'.code, '!'.code,
            '？'.code, '?'.code, '、'.code, '｜'.code, '|'.code, '/'.code, '\\'.code,
            '_'.code, ':'.code, ';'.code, '；'.code, '（'.code, '）'.code, '('.code,
            ')'.code, '【'.code, '】'.code, '['.code, ']'.code, '「'.code, '」'.code,
            '『'.code, '』'.code, '"'.code, '\''.code, '~'.code, '`'.code,
        )

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
