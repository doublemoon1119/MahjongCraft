package com.doublemoon1119.mahjongcraft.logic.util

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 從一組可互換的牌（例如碰牌時符合條件的手牌）中挑出 [count] 張供實際消耗使用，優先選一般牌；
 * [Tile.Extension] 牌型（例如日麻赤五）只在一般牌數量不足時才會被選中，讓玩家自然保留手上的特殊
 * 變體牌。規則中立：不認得任何特定擴充牌型，任何規則模組的可互換牌選擇情境都能重用。
 */
fun List<IdentifiedTile>.preferPlainTiles(count: Int): List<IdentifiedTile> = sortedBy { it.tile is Tile.Extension }.take(count)
