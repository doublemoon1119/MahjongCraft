package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType

/**
 * 裏寶牌 (Ura Dora) 役種檢測器。
 *
 * 裏寶牌只有在立直時才會翻開計演算法。
 * 裏寶牌指示牌 +1 即為裏寶牌，計算方式與寶牌相同。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張（計入裏寶牌計算）。
 * @param uraDoraIndicators 裏寶牌指示牌列表。
 * @param setAsideTiles 不在手牌中但仍計入裏寶牌的牌，例如三人麻將拔出的北。
 * @param usesThreePlayerTiles 是否使用沒有二～八萬的三人麻將牌組。
 * @return 裏寶牌番數結果。
 */
fun calculateRiichiUraDora(
    hand: Hand,
    winningTile: Tile,
    uraDoraIndicators: List<Tile>,
    setAsideTiles: List<Tile> = emptyList(),
    usesThreePlayerTiles: Boolean = false,
): RiichiYakuResult {
    val allTiles = buildList {
        hand.allTiles.mapTo(this) { it.tile }
        add(winningTile)
        addAll(setAsideTiles)
    }

    val uraDoraCount = uraDoraIndicators.sumOf { indicator ->
        val doraTile = riichiNextDora(indicator, usesThreePlayerTiles)
        allTiles.count { it == doraTile }
    }

    return RiichiYakuResult.han(RiichiYakuType.UraDora, uraDoraCount)
}
