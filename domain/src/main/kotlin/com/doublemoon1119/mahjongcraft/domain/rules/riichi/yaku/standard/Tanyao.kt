package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.isTerminal

/**
 * 斷么九 (Tanyao) 役種檢測器。
 *
 * 全部牌都使用 2-8 的數牌，無視麼九牌（1、9）與字牌。
 * 食斷 (Open Tanyao) 可透過 [isMenzen] 參數控制是否允許。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張。
 * @param isMenzen 是否為門前清（無副露）。
 * @param allowOpenTanyao 是否允許食斷。
 * @return 斷么九役種結果，若不符合則返回 null。
 */
fun calculateTanyao(
    hand: Hand,
    winningTile: Tile,
    isMenzen: Boolean,
    allowOpenTanyao: Boolean
): YakuResult? {
    val allTiles = hand.allTiles.map { it.tile } + winningTile

    check(allTiles.none { it is Tile.Flower }) { "Flower tiles should not exist in Riichi Mahjong" }

    val hasTerminalOrHonor = allTiles.any { tile ->
        when (tile) {
            is Tile.Numeric -> tile.isTerminal
            is Tile.Honor -> true
            else -> false
        }
    }

    if (hasTerminalOrHonor) {
        return null
    }

    if (!isMenzen && !allowOpenTanyao) {
        return null
    }

    return YakuResult.han(YakuType.Tanyao, 1)
}
