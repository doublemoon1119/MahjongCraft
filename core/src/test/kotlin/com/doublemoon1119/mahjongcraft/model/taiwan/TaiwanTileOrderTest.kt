package com.doublemoon1119.mahjongcraft.model.taiwan

import com.doublemoon1119.mahjongcraft.model.base.Tile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 針對台灣麻將排序規則 [TaiwanTileOrder] 進行單元測試。
 */
class TaiwanTileOrderTest {

    /**
     * 測試中、發、白的排序順序。
     * 台灣麻將習慣順序為：中、發、白。
     */
    @Test
    fun `test Taiwan style honor sorting`() {
        val red = Tile.Honor.Red     // 中
        val green = Tile.Honor.Green // 發
        val white = Tile.Honor.White // 白

        val list = listOf(white, green, red)
        val sorted = list.sortedWith(TaiwanTileOrder)

        // 預期順序：中 -> 發 -> 白
        assertEquals(listOf(red, green, white), sorted)
    }

    /**
     * 測試花牌在台灣麻將中的排序位置。
     * 花牌應排在字牌之後，且遵循春夏秋冬、梅蘭竹菊的順序。
     */
    @Test
    fun `test Taiwan style flower sorting`() {
        val spring = Tile.Flower.Spring
        val autumn = Tile.Flower.Autumn
        val east = Tile.Honor.East
        val nineDot = Tile.Numeric(Tile.Suit.Dot, 9)

        val list = listOf(spring, east, nineDot, autumn)
        val sorted = list.sortedWith(TaiwanTileOrder)

        // 預期順序：9 筒 -> 東風 -> 春 -> 秋
        assertEquals(listOf(nineDot, east, spring, autumn), sorted)
    }
}