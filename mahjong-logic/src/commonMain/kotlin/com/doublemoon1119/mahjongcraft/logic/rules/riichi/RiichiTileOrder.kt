package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileInterpretationPolicy

/**
 * 日本麻將 (Riichi) 標準排序策略。
 * 排序權重：
 * 1. 數牌依萬、筒、索排序，各花色由一至九；同數值時普通牌在赤寶牌之前。
 * 2. 字牌依東、南、西、北、白、發、中排序。
 * 3. 花牌與尚未由日麻規則解讀的擴充牌 (若存在，排在最後)。
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
                Tile.Suit.Character -> 10.0 // 萬子基數
                Tile.Suit.Dot -> 20.0 // 筒子基數
                Tile.Suit.Bamboo -> 30.0 // 條子基數
            }
            // 赤寶牌權重增加 0.1，使其排在同數值的普通牌之後
            suitBase + tile.value
        }
        is Tile.Honor -> when (tile) {
            Tile.Honor.East -> 41.0
            Tile.Honor.South -> 42.0
            Tile.Honor.West -> 43.0
            Tile.Honor.North -> 44.0
            Tile.Honor.White -> 45.0 // 日麻順序：白、發、中
            Tile.Honor.Green -> 46.0
            Tile.Honor.Red -> 47.0
        }
        is Tile.Extension -> if (RiichiTileInterpretationPolicy.isRedDora(tile)) {
            getWeight(RiichiTileInterpretationPolicy.canonicalize(tile)) + 0.1
        } else {
            100.0
        }
    }
}
