package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.Tile
import com.doublemoon1119.mahjongcraft.model.TileOrder


/**
 * 日本麻將 (Riichi) 標準排序策略。
 * 排序權重：
 * 1. 數牌 (萬 > 筒 > 條)，同數值時「普通牌」在「赤寶牌」之前。
 * 2. 字牌 (東南西北 > 白發中)。
 * 3. 花牌 (若存在，排在最後)。
 */
object RiichiTileOrder : TileOrder {
    override fun compare(t1: Tile, t2: Tile): Int = getWeight(t1).compareTo(getWeight(t2))

    /**
     * 計算牌面的排序權重。
     * 採用 Double 類型以利於處理赤寶牌的微小權重差異。
     *
     * @param tile 欲計算權重的麻將牌。
     * @return 權重數值。
     */
    private fun getWeight(tile: Tile): Double = when (tile) {
        is Tile.Numeric -> {
            val suitBase = when (tile.suit) {
                Tile.Suit.Characters -> 10.0 // 萬子基數
                Tile.Suit.Dots       -> 20.0 // 筒子基數
                Tile.Suit.Bamboos    -> 30.0 // 條子基數
            }
            // 赤寶牌權重增加 0.1，使其排在同數值的普通牌之後
            suitBase + tile.value + (if (tile.isRed) 0.1 else 0.0)
        }
        is Tile.Honor -> when (tile) {
            Tile.Honor.East  -> 41.0
            Tile.Honor.South -> 42.0
            Tile.Honor.West  -> 43.0
            Tile.Honor.North -> 44.0
            Tile.Honor.White -> 45.0 // 日麻順序：白、發、中
            Tile.Honor.Green -> 46.0
            Tile.Honor.Red   -> 47.0
        }
        is Tile.Flower -> 100.0 // 花牌排最後 (沒有用到)
    }
}