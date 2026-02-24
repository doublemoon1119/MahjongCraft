package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.base.Tile
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * 針對 [RiichiTileOrder] 進行單元測試。
 */
class RiichiTileOrderTest {
    /**
     * 測試白、中的排序順序。
     */
    @Test
    fun `test Riichi style sorting`() {
        val red = Tile.Honor.Red     // 中
        val white = Tile.Honor.White // 白

        val list = listOf(red, white)
        val sorted = list.sortedWith(RiichiTileOrder)

        // 日本麻將：白在前，中在後
        assertEquals(listOf(white, red), sorted)
    }

    /**
     * 測試赤寶牌與普通牌的排序順序。
     */
    @Test
    fun `test sorting with red five`() {
        val normalFive = Tile.Numeric(Tile.Suit.Dot, 5, isRed = false)
        val redFive = Tile.Numeric(Tile.Suit.Dot, 5, isRed = true)
        val sixDot = Tile.Numeric(Tile.Suit.Dot, 6)

        val list = listOf(sixDot, redFive, normalFive)
        val sorted = list.sortedWith(RiichiTileOrder)

        // 預期順序：普通 5 筒 -> 赤 5 筒 -> 6 筒
        assertEquals(listOf(normalFive, redFive, sixDot), sorted)
    }
}