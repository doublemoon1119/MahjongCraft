package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.util.withoutRed

/**
 * 裏寶牌 (Ura Dora) 役種檢測器。
 *
 * 裏寶牌只有在立直時才會翻開計演算法。
 * 裏寶牌指示牌 +1 即為裏寶牌，計算方式與寶牌相同。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張（計入裏寶牌計算）。
 * @param uraDoraIndicators 裏寶牌指示牌列表。
 * @return 裏寶牌番數結果。
 */
fun calculateUraDora(
    hand: Hand,
    winningTile: Tile,
    uraDoraIndicators: List<Tile>,
): YakuResult {
    val allTiles = hand.allTiles.map { it.tile } + winningTile

    val uraDoraCount = uraDoraIndicators.sumOf { indicator ->
        val doraTile = getNextDora(indicator)
        allTiles.count { it.withoutRed == doraTile }
    }

    return YakuResult.han(YakuType.UraDora, uraDoraCount)
}
