package com.example.flower_show.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WordPieceTokenizerTest {
    @Test
    fun encodeAddsSpecialTokensAndPadsToMaxLength() {
        val tokenizer = tokenizer(
            "hello" to 200,
            "world" to 201,
        )

        val encoded = tokenizer.encode("Hello world", maxLength = 6)

        assertArrayEquals(longArrayOf(101, 200, 201, 102, 0, 0), encoded.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1, 0, 0), encoded.attentionMask)
        assertArrayEquals(longArrayOf(0, 0, 0, 0, 0, 0), encoded.tokenTypeIds)
    }

    @Test
    fun encodeSplitsKnownWordPieces() {
        val tokenizer = tokenizer(
            "flow" to 300,
            "##er" to 301,
        )

        val encoded = tokenizer.encode("flower", maxLength = 5)

        assertArrayEquals(longArrayOf(101, 300, 301, 102, 0), encoded.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1, 0), encoded.attentionMask)
    }

    @Test
    fun encodeSeparatesCjkCharactersAndPunctuation() {
        val tokenizer = tokenizer(
            "a" to 10,
            "\u4e2d" to 11,
        )

        val encoded = tokenizer.encode("a#\u4e2d", maxLength = 5)

        assertArrayEquals(longArrayOf(101, 10, 11, 102, 0), encoded.inputIds)
    }

    @Test
    fun encodeUsesUnknownTokenForMissingPieces() {
        val tokenizer = tokenizer()

        val encoded = tokenizer.encode("missing", maxLength = 4)

        assertArrayEquals(longArrayOf(101, 100, 102, 0), encoded.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 0), encoded.attentionMask)
    }

    @Test
    fun encodeTruncatesTokensButKeepsSeparatorToken() {
        val tokenizer = tokenizer(
            "one" to 210,
            "two" to 211,
            "three" to 212,
        )

        val encoded = tokenizer.encode("one two three", maxLength = 4)

        assertArrayEquals(longArrayOf(101, 210, 211, 102), encoded.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1), encoded.attentionMask)
    }

    private fun tokenizer(vararg extraTokens: Pair<String, Int>): WordPieceTokenizer {
        val vocab = linkedMapOf(
            "[PAD]" to 0,
            "[UNK]" to 100,
            "[CLS]" to 101,
            "[SEP]" to 102,
        )
        vocab += extraTokens

        val constructor = WordPieceTokenizer::class.java.getDeclaredConstructor(
            Map::class.java,
            Integer.TYPE,
        )
        constructor.isAccessible = true
        return constructor.newInstance(vocab, 100)
    }
}
