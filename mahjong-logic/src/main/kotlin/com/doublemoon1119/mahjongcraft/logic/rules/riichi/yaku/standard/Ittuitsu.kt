package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.util.withoutRed

/**
 * 一氣通貫 (Ittuitsu) 役種檢測器。
 *
 * 萬、筒、條三種花色中，某種花色同時擁有 123、456、789 三個順子。
 * 必須門前清。
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張。
 * @param isMenzen 是否為門前清（無副露）。
 * @return 一氣通貫役種結果，若不符合則返回 null。
 */
fun calculateIttuitsu(
    hand: Hand,
    winningTile: Tile,
    isMenzen: Boolean
): YakuResult? {
    val allTiles = (hand.allTiles.map { it.tile } + winningTile)
        .map { it.withoutRed }

    for (suit in listOf(Tile.Suit.Character, Tile.Suit.Dot, Tile.Suit.Bamboo)) {
        val suitTiles = allTiles.filter { it is Tile.Numeric && it.suit == suit }
        val hasLower = hasMelds(suitTiles, 1, 2, 3)
        val hasMiddle = hasMelds(suitTiles, 4, 5, 6)
        val hasUpper = hasMelds(suitTiles, 7, 8, 9)

        if (hasLower && hasMiddle && hasUpper) {
            val han = if (isMenzen) 2 else 1
            return YakuResult.han(YakuType.Ittuitsu, han)
        }
    }

    return null
}

/**
 * 檢查是否包含指定數值的三張牌（可來自順子或刻子）。
 */
private fun hasMelds(tiles: List<Tile>, first: Int, second: Int, third: Int): Boolean {
    val counts = tiles.filterIsInstance<Tile.Numeric>().groupBy { it.value }.mapValues { it.value.size }

    return (counts[first] ?: 0) > 0 && (counts[second] ?: 0) > 0 && (counts[third] ?: 0) > 0
}
