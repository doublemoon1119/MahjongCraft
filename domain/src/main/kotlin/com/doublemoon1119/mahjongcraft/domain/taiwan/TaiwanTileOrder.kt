package com.doublemoon1119.mahjongcraft.domain.taiwan

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.base.TileOrder

/**
 * 台灣麻將標準排序策略。
 * * 排序權重說明：
 * 1. 數牌：萬 (10+) > 筒 (20+) > 條 (30+)。
 * 2. 字牌：風牌 (41-44) > 三元牌 (45-47，順序為中、發、白)。
 * 3. 花牌：四季 (81-84) > 四君子 (85-88)。
 */
object TaiwanTileOrder : TileOrder {
    override fun compare(t1: Tile, t2: Tile): Int = getWeight(t1).compareTo(getWeight(t2))

    private fun getWeight(tile: Tile): Double = when (tile) {
        is Tile.Numeric -> {
            val suitBase = when (tile.suit) {
                Tile.Suit.Character -> 10.0
                Tile.Suit.Dot       -> 20.0
                Tile.Suit.Bamboo    -> 30.0
            }
            suitBase + tile.value.toDouble()
        }
        is Tile.Honor -> when (tile) {
            Tile.Honor.East  -> 41.0
            Tile.Honor.South -> 42.0
            Tile.Honor.West  -> 43.0
            Tile.Honor.North -> 44.0
            Tile.Honor.Red   -> 45.0 // 台麻順序：中
            Tile.Honor.Green -> 46.0 // 發
            Tile.Honor.White -> 47.0 // 白
        }
        is Tile.Flower -> when (tile) {
            Tile.Flower.Spring -> 81.0
            Tile.Flower.Summer -> 82.0
            Tile.Flower.Autumn -> 83.0
            Tile.Flower.Winter -> 84.0
            Tile.Flower.Plum   -> 85.0
            Tile.Flower.Orchid -> 86.0
            Tile.Flower.Bamboo -> 87.0
            Tile.Flower.Chrysanthemum -> 88.0
        }
    }
}