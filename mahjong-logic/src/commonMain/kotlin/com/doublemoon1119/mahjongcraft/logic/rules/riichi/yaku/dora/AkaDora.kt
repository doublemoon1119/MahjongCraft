package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 赤寶牌 (Aka Dora) 役種檢測器。
 *
 * 赤寶牌為帶有 [Tile.Numeric.isRed] 標記的牌（5 萬、5 筒、5 條）。
 * 每一張赤寶牌額外提供 1 翻。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張（計入赤寶牌計算）。
 * @return 赤寶牌番數結果。
 */
fun calculateAkaDora(
    hand: Hand,
    winningTile: Tile,
): YakuResult {
    var count = 0

    // 檢查所有牌（立牌、副露、胡牌張）中的赤寶牌
    val allTiles = hand.allTiles.map { it.tile } + winningTile

    allTiles.forEach { tile ->
        if (tile is Tile.Numeric && tile.isRed) {
            count++
        }
    }

    return YakuResult.han(YakuType.AkaDora, count)
}
