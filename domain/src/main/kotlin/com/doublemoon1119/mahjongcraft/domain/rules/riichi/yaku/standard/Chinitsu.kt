package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed

/**
 * 清一色 (Chinitsu) 役種檢測器。
 *
 * 手牌僅由一種數牌花色組成（無字牌）。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張。
 * @return 清一色役種結果，若不符合則返回 null。
 */
fun calculateChinitsu(
    hand: Hand,
    winningTile: Tile
): YakuResult? {
    val allTiles = (hand.allTiles.map { it.tile } + winningTile)
        .map { it.withoutRed }

    val numericTiles = allTiles.filterIsInstance<Tile.Numeric>()
    val honorTiles = allTiles.filterIsInstance<Tile.Honor>()

    if (numericTiles.isEmpty() || honorTiles.isNotEmpty()) {
        return null
    }

    val suits = numericTiles.map { it.suit }.toSet()

    if (suits.size != 1) {
        return null
    }

    return YakuResult.han(YakuType.Chinitsu, 6)
}
