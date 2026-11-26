package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile

/**
 * 一個牌山排列開門之後的結果。
 *
 * @property drawOrder 一般摸牌順序，從配牌開始依序摸。
 * @property reservedWallTiles 不在一般摸牌順序中的規則保留牌，順序與規則的保留牌區相同。規則不保留牌時為空。
 */
data class OpenedTileWallOrder(
    val drawOrder: List<IdentifiedTile>,
    val reservedWallTiles: List<IdentifiedTile>,
)
