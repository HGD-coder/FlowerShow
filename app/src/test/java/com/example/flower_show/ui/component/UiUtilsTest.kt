package com.example.flower_show.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UiUtilsTest {

    // ── Positive tests / 正向测试 ──

    @Test
    fun formatCount_exact10k_displays1_0wan() {
        assertEquals("1.0万", formatCount(10000))
    }

    @Test
    fun formatCount_125k_displays12_5wan() {
        assertEquals("12.5万", formatCount(125000))
    }

    @Test
    fun formatCount_8900_displaysRaw() {
        assertEquals("8900", formatCount(8900))
    }

    @Test
    fun formatCount_100k_displays10_0wan() {
        assertEquals("10.0万", formatCount(100000))
    }

    @Test
    fun formatCount_1000k_displays100_0wan() {
        assertEquals("100.0万", formatCount(1000000))
    }

    @Test
    fun formatCount_1_displays1() {
        assertEquals("1", formatCount(1))
    }

    @Test
    fun formatCount_9999_displays9999() {
        assertEquals("9999", formatCount(9999))
    }

    @Test
    fun formatCount_10001_displays1_0wan() {
        assertEquals("1.0万", formatCount(10001))
    }

    // ── Boundary tests / 边界测试 ──

    @Test
    fun formatCount_zero_displays0() {
        assertEquals("0", formatCount(0))
    }

    @Test
    fun formatCount_maxInt_handlesGracefully() {
        val result = formatCount(Int.MAX_VALUE)
        // 21亿/10000 = 214748万.3647/1000 → 214748万.3
        assertEquals("214748.3万", result)
    }

    @Test
    fun formatCount_negative_keepsSign() {
        // Negative counts shouldn't happen, but test behavior
        assertEquals("-1", formatCount(-1))
    }

    @Test
    fun formatCount_negativeLarge_displaysAsIs() {
        // Negative numbers don't go through the "万" branch (n < 10000)
        assertEquals("-10000", formatCount(-10000))
    }

    @Test
    fun formatCount_10999_roundsDownCorrectly() {
        // 10999 → quotient=1, remainder=999, /1000=0 → "1.0万"
        assertEquals("1.0万", formatCount(10999))
    }

    @Test
    fun formatCount_11000_displays1_1wan() {
        assertEquals("1.1万", formatCount(11000))
    }

    // ── Reverse tests / 反向测试 ──

    @Test
    fun formatCount_negativeZero_isZero() {
        // Kotlin Int: -0 == 0
        assertEquals("0", formatCount(0))
    }
}
