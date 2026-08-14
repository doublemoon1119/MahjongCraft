package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 清一色 (Chinitsu) 役種檢測器。
 *
 * 手牌僅由一種數牌花色組成（無字牌）。
 * 門前清為 6 翻，非門前清為 5 翻。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張。
 * @param isMenzen 是否為門前清（無副露）。
 * @return 清一色役種結果，若不符合則返回 null。
 */
fun calculateChinitsu(
    hand: Hand,
    winningTile: Tile,
    isMenzen: Boolean,
): YakuResult? {
    val allTiles = (hand.allTiles.map { it.tile } + winningTile)
        .map { it.riichiCanonical }

    val numericTiles = allTiles.filterIsInstance<Tile.Numeric>()
    val honorTiles = allTiles.filterIsInstance<Tile.Honor>()

    if (numericTiles.isEmpty() || honorTiles.isNotEmpty()) {
        return null
    }

    val suits = numericTiles.map { it.suit }.toSet()

    if (suits.size != 1) {
        return null
    }

    val han = if (isMenzen) 6 else 5
    return YakuResult.han(YakuType.Chinitsu, han)
}
