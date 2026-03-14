package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

/**
 * 混一色 (Honitsu) 役種檢測器。
 *
 * 手牌由一種數牌花色 + 字牌組成。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張。
 * @return 混一色役種結果，若不符合則返回 null。
 */
fun calculateHonitsu(
    hand: Hand,
    winningTile: Tile
): YakuResult? {
    val allTiles = (hand.allTiles.map { it.tile } + winningTile)
        .map { it.withoutRed }

    val numericTiles = allTiles.filterIsInstance<Tile.Numeric>()

    if (numericTiles.isEmpty()) {
        return null
    }

    val suits = numericTiles.map { it.suit }.toSet()

    if (suits.size != 1) {
        return null
    }

    return YakuResult.han(YakuType.Honitsu, 3)
}
