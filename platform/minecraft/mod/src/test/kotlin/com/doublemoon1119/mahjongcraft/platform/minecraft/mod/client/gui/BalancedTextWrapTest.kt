package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證平均換行寬度在不增加行數的前提下讓各行長度接近。 */
class BalancedTextWrapTest {
    /**
     * 以固定字寬模擬逐字換行：[length] 個字、每字 [glyphWidth] 寬。
     *
     * @param length 字數。
     * @param glyphWidth 每個字的寬度。
     * @return 依寬度回傳行數的函式。
     */
    private fun glyphLines(length: Int, glyphWidth: Int): (Int) -> Int = { width ->
        val perLine = (width / glyphWidth).coerceAtLeast(1)
        (length + perLine - 1) / perLine
    }

    /** 十七個字在十六字寬內換行時，兩行平分為九字與八字，而不是十六字加一字。 */
    @Test
    fun `splits the lines evenly instead of leaving one glyph behind`() {
        val lines = glyphLines(length = 17, glyphWidth = 9)

        val width = balancedWrapWidth(maximumWidth = 16 * 9, lineCount = lines)

        assertEquals(9 * 9, width)
        assertEquals(2, lines(width))
    }

    /** 一行放得下時維持最大寬度。 */
    @Test
    fun `keeps the maximum width for a single line`() {
        assertEquals(144, balancedWrapWidth(maximumWidth = 144, lineCount = glyphLines(length = 5, glyphWidth = 9)))
    }

    /** 行數不因平均而增加。 */
    @Test
    fun `never adds a line`() {
        val lines = glyphLines(length = 40, glyphWidth = 9)

        val width = balancedWrapWidth(maximumWidth = 150, lineCount = lines)

        assertEquals(lines(150), lines(width))
        assertEquals(lines(150), lines(width + 1))
    }

    /** 寬度不足一時視為一。 */
    @Test
    fun `treats a width below one as one`() {
        assertEquals(1, balancedWrapWidth(maximumWidth = 0) { 1 })
    }
}
