package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.util.withoutRed

/**
 * 混一色 (Honitsu) 役種檢測器。
 *
 * 手牌由一種數牌花色 + 字牌組成。
 * 門前清為 3 翻，非門前清為 2 翻。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張。
 * @param isMenzen 是否為門前清（無副露）。
 * @return 混一色役種結果，若不符合則返回 null。
 */
fun calculateHonitsu(
    hand: Hand,
    winningTile: Tile,
    isMenzen: Boolean,
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

    val han = if (isMenzen) 3 else 2
    return YakuResult.han(YakuType.Honitsu, han)
}
