package io.github.androidpoet.weekview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventTextModeTest {
    private val lineHeight = 16f
    private val padding = 4f

    private fun mode(
        width: Float,
        height: Float,
        longestWord: Float,
    ) = eventTextMode(width, height, lineHeight, longestWord, padding)

    @Test
    fun test_eventTextMode_wordsFitWidth_wraps() {
        val result = mode(width = 120f, height = 60f, longestWord = 50f)

        assertEquals(EventTextMode.Wrapped, result)
    }

    @Test
    fun test_eventTextMode_wordWiderThanRoomyBlock_usesOneEllipsizedLine() {
        val result = mode(width = 60f, height = 60f, longestWord = 80f)

        assertEquals(EventTextMode.SingleLine, result)
    }

    @Test
    fun test_eventTextMode_narrowTallBlock_rotatesText() {
        val result = mode(width = 15f, height = 400f, longestWord = 30f)

        assertEquals(EventTextMode.Vertical, result)
    }

    @Test
    fun test_eventTextMode_narrowShortBlock_usesOneLineOrHides() {
        val readable = mode(width = 30f, height = 20f, longestWord = 40f)
        val tooNarrow = mode(width = 15f, height = 20f, longestWord = 40f)

        assertEquals(EventTextMode.SingleLine, readable)
        assertEquals(EventTextMode.Hidden, tooNarrow)
    }

    @Test
    fun test_eventTextMode_blockShorterThanALine_hides() {
        val result = mode(width = 200f, height = 8f, longestWord = 30f)

        assertEquals(EventTextMode.Hidden, result)
    }

    @Test
    fun test_prefersDarkText_lightAndDarkBackgrounds_picksReadableText() {
        val onAmber = prefersDarkText(0xFFF59E0B.toInt())
        val onWhite = prefersDarkText(0xFFFFFFFF.toInt())
        val onBlue = prefersDarkText(0xFF2563EB.toInt())
        val onSlate = prefersDarkText(0xFF475569.toInt())

        assertTrue(onAmber && onWhite)
        assertTrue(!onBlue && !onSlate)
    }

    @Test
    fun test_verticalTextScale_blockNarrowerThanALine_shrinksToFit() {
        val narrow = verticalTextScale(width = 12f, lineHeight = lineHeight)
        val wide = verticalTextScale(width = 40f, lineHeight = lineHeight)

        assertTrue(narrow < 1f && narrow * lineHeight <= 12f)
        assertEquals(1f, wide)
    }
}
