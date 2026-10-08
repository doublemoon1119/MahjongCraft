package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 寶牌 (Dora) 役種檢測器。
 *
 * 根據寶牌指示牌計算寶牌提供的番數。
 * 寶牌指示牌 +1 即為寶牌，例如：
 * - 1 萬 → 2 萬為寶牌
 * - 9 萬 → 1 萬為寶牌（循環）
 * - 東 → 南為寶牌
 * - 白 → 發為寶牌
 *
 * @param hand 玩家手牌（包含立牌與副露）。
 * @param winningTile 胡牌張（計入寶牌計算）。
 * @param doraIndicators 寶牌指示牌列表。
 * @param setAsideTiles 不在手牌中但仍計入寶牌的牌，例如三人麻將拔出的北。
 * @param usesThreePlayerTiles 是否使用沒有二～八萬的三人麻將牌組。
 * @return 寶牌番數結果。
 */
fun calculateDora(
    hand: Hand,
    winningTile: Tile,
    doraIndicators: List<Tile>,
    setAsideTiles: List<Tile> = emptyList(),
    usesThreePlayerTiles: Boolean = false,
): YakuResult {
    val allTiles = hand.allTiles.map { it.tile } + winningTile + setAsideTiles

    val doraCount = doraIndicators.sumOf { indicator ->
        val doraTile = getNextDora(indicator, usesThreePlayerTiles)
        allTiles.count { it.riichiCanonical == doraTile }
    }

    return YakuResult.han(YakuType.Dora, doraCount)
}

/**
 * 取得寶牌指示牌的下一張牌作為寶牌。
 *
 * 三人麻將的牌組沒有二～八萬，因此一萬與九萬互為指示牌。
 *
 * @param indicator 寶牌指示牌。
 * @param usesThreePlayerTiles 是否使用沒有二～八萬的三人麻將牌組。
 * @return 對應的寶牌。
 */
internal fun getNextDora(indicator: Tile, usesThreePlayerTiles: Boolean = false): Tile = when (val canonicalIndicator = indicator.riichiCanonical) {
    // 三人麻將的萬子只有一萬與九萬
    is Tile.Numeric if usesThreePlayerTiles && canonicalIndicator.suit == Tile.Suit.Character ->
        Tile.Numeric(Tile.Suit.Character, if (canonicalIndicator.value == 1) 9 else 1)
    // 數牌：循環 1-9
    is Tile.Numeric -> {
        val nextValue = if (canonicalIndicator.value == 9) 1 else canonicalIndicator.value + 1
        Tile.Numeric(canonicalIndicator.suit, nextValue)
    }
    // 字牌：東→南→西→北→東（循環）
    is Tile.Honor.East -> Tile.Honor.South
    is Tile.Honor.South -> Tile.Honor.West
    is Tile.Honor.West -> Tile.Honor.North
    is Tile.Honor.North -> Tile.Honor.East
    // 三元牌：白→發→中→白（循環）
    is Tile.Honor.White -> Tile.Honor.Green
    is Tile.Honor.Green -> Tile.Honor.Red
    is Tile.Honor.Red -> Tile.Honor.White
    // 未由日麻規則明確定義的擴充牌不作為寶牌指示牌
    is Tile.Extension -> canonicalIndicator
}
