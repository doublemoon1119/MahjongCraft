package com.doublemoon1119.mahjongcraft.logic.util

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 檢查是否為老頭牌（1 或 9 的數牌）。
 */
val Tile.isTerminal: Boolean
    get() = (this as? Tile.Numeric)?.let { it.value == 1 || it.value == 9 } ?: false

/**
 * 檢查是否為數牌（不含字牌）。
 */
val Tile.isNumeric: Boolean
    get() = this is Tile.Numeric

/**
 * 檢查是否為字牌。
 */
val Tile.isHonor: Boolean
    get() = this is Tile.Honor

/**
 * 檢查是否為風牌（東南西北，不含三元牌）。
 */
val Tile.isWind: Boolean
    get() = this is Tile.Honor && this in setOf(Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North)

/**
 * 取得牌的花色，若為數牌則返回花色，否則返回 null。
 */
val Tile.suit: Tile.Suit?
    get() = (this as? Tile.Numeric)?.suit
