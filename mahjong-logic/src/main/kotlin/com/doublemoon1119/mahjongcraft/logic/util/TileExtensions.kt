package com.doublemoon1119.mahjongcraft.logic.util

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 移除赤寶牌標記，將赤寶牌視為普通牌。
 *
 * @return 去除赤寶牌標記後的牌。
 */
val Tile.withoutRed: Tile
    get() = when (this) {
        is Tile.Numeric -> this.copy(isRed = false)
        else -> this
    }

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
 * 取得牌的花色，若為數牌則返回花色，否則返回 null。
 */
val Tile.suit: Tile.Suit?
    get() = (this as? Tile.Numeric)?.suit
