package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening

/**
 * 一張牌在實體牌牆的結構座標（面、墩、層）。
 *
 * @property side 規則牌牆布局中的零基底面索引，與 [WallOpening.wallSideOffsetFromDealer] 使用相同順序。
 * 實際側面由 [TileWallLayout.sideArrangement] 指定；不保證索引 `0` 永遠在莊家面前。
 * @property stack 該面內的零基底墩序號，從桌外面向該面時的右端起算（`0` 為最右側的墩）。
 * @property layer 墩內的層數；`0` 為下層，`1` 為上層。
 */
data class TileWallPosition(
    val side: Int,
    val stack: Int,
    val layer: Int,
)
