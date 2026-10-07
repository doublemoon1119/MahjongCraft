package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.local

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/** 古役三連刻、五門齊的翻數。 */
private const val TWO_HAN = 2

/** 古役一色三同順門前清的翻數。 */
private const val ISSHOKU_SANJUN_CLOSED_HAN = 3

/** 古役一色三同順需要的相同順子數量。 */
private const val ISSHOKU_SANJUN_COUNT = 3

/** 大車輪類役滿使用的最小數字。 */
private const val WHEEL_LOWEST = 2

/** 大車輪類役滿使用的最大數字。 */
private const val WHEEL_HIGHEST = 8

/** 十二落抬需要的副露數量。 */
private const val SHIIARU_RAOTAI_MELDS = 4

/** 風牌。 */
private val WINDS: Set<Tile> = setOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North)

/** 三元牌。 */
private val DRAGONS: Set<Tile> = setOf(Tile.Honor.Red, Tile.Honor.Green, Tile.Honor.White)

/**
 * 十二落抬 (Shiiaru Raotai)：四組面子全部以吃、碰、明槓或加槓副露，剩下一張單騎和牌；有暗槓時不成立。
 *
 * @param hand 和牌前的手牌，不含和牌張。
 * @return 1 翻；不成立時為 null。
 */
fun calculateShiiaruRaotai(hand: Hand): YakuResult? {
    val melds = hand.exposedMelds
    if (melds.size != SHIIARU_RAOTAI_MELDS || melds.any { it.type == MeldType.CLOSED_KAN }) return null
    if (hand.standingTiles.size != 1) return null
    return YakuResult.han(YakuType.ShiiaruRaotai, 1)
}

/**
 * 五門齊 (Uumen Chii)：和牌形同時含萬子、筒子、索子、風牌與三元牌；七對子也成立。
 *
 * @param hand 和牌前的手牌，不含和牌張。
 * @param winningTile 和牌張。
 * @return 2 翻；不成立時為 null。
 */
fun calculateUumenChii(hand: Hand, winningTile: Tile): YakuResult? {
    val tiles = hand.allTiles.map { it.tile.riichiCanonical } + winningTile.riichiCanonical
    val suits = tiles.mapNotNullTo(mutableSetOf()) { (it as? Tile.Numeric)?.suit }
    if (suits.size != Tile.Suit.entries.size) return null
    if (tiles.none { it in WINDS } || tiles.none { it in DRAGONS }) return null
    return YakuResult.han(YakuType.UumenChii, TWO_HAN)
}

/**
 * 三連刻 (Sanrenkou)：同花色數字連續的三組刻子或槓子，例如 333m、444m、555m；四組連續仍只計一次。
 *
 * @param handStructure 手牌結構。
 * @return 2 翻；不成立時為 null。
 */
fun calculateSanrenkou(handStructure: HandStructure): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null
    val tripletTiles = (standard.mentsus + standard.fuuro.map { it.mentsu }).mapNotNull { mentsu ->
        when (mentsu) {
            is Mentsu.Kotsu -> mentsu.tile
            is Mentsu.Ankan -> mentsu.tile
            is Mentsu.Minkan -> mentsu.tile
            is Mentsu.Kakan -> mentsu.tile
            is Mentsu.Shuntsu -> null
        } as? Tile.Numeric
    }
    val hasRun = tripletTiles.groupBy { it.suit }.values.any { tiles ->
        val values = tiles.map { it.value }.toSet()
        values.any { it + 1 in values && it + 2 in values }
    }
    return if (hasRun) YakuResult.han(YakuType.Sanrenkou, TWO_HAN) else null
}

/**
 * 一色三同順 (Isshoku Sanjun)：同花色同數字的三組順子，例如 123m 三組；門前清 3 翻，副露 2 翻。
 *
 * @param handStructure 手牌結構。
 * @param isMenzen 是否門前清。
 * @return 依門清與否的翻數；不成立時為 null。
 */
fun calculateIsshokuSanjun(handStructure: HandStructure, isMenzen: Boolean): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null
    val sequences = (standard.mentsus + standard.fuuro.map { it.mentsu }).filterIsInstance<Mentsu.Shuntsu>()
    val hasTriple = sequences.groupingBy { it.headTile }.eachCount().values.any { it >= ISSHOKU_SANJUN_COUNT }
    if (!hasTriple) return null
    return YakuResult.han(YakuType.IsshokuSanjun, if (isMenzen) ISSHOKU_SANJUN_CLOSED_HAN else TWO_HAN)
}

/**
 * 大車輪、大竹林、大數鄰：門前清，同一花色的 2～8 各兩張，分別為筒子、索子、萬子。
 *
 * @param hand 和牌前的手牌，不含和牌張。
 * @param winningTile 和牌張。
 * @param isMenzen 是否門前清。
 * @return 對應花色的役滿；不成立時為 null。
 */
fun calculateWheelYakuman(
    hand: Hand,
    winningTile: Tile,
    isMenzen: Boolean,
): YakuResult? {
    if (!isMenzen || hand.exposedMelds.isNotEmpty()) return null
    val tiles = hand.allTiles.map { it.tile.riichiCanonical } + winningTile.riichiCanonical
    val numeric = tiles.filterIsInstance<Tile.Numeric>()
    if (numeric.size != tiles.size) return null
    val suit = numeric.map { it.suit }.distinct().singleOrNull() ?: return null
    val counts = numeric.groupingBy { it.value }.eachCount()
    if (counts != (WHEEL_LOWEST..WHEEL_HIGHEST).associateWith { 2 }) return null
    val yaku = when (suit) {
        Tile.Suit.Dot -> YakuType.Daisharin
        Tile.Suit.Bamboo -> YakuType.Daichikurin
        Tile.Suit.Character -> YakuType.Daisuurin
    }
    return YakuResult.yakuman(yaku)
}

/**
 * 大七星 (Daichisei)：七種字牌各一對的七對子。
 *
 * @param handStructure 手牌結構。
 * @return 雙倍役滿；不成立時為 null。
 */
fun calculateDaichisei(handStructure: HandStructure): YakuResult? {
    val chiitoitsu = handStructure as? HandStructure.Chiitoitsu ?: return null
    val pairs = chiitoitsu.pairs.map { it.tile }
    if (pairs.toSet() != WINDS + DRAGONS) return null
    return YakuResult.doubleYakuman(YakuType.Daichisei)
}
