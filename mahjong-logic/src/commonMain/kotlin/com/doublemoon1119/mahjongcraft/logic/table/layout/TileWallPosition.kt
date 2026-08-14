package com.doublemoon1119.mahjongcraft.logic.table.layout

/**
 * 一張牌在實體牌牆的結構座標，供未來 3D 呈現使用。
 *
 * @property side 相對莊家牌牆面的零基底偏移，與 [com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening.wallSideOffsetFromDealer]
 * 同一套座標系（`0` 為莊家面前，依逆時針方向遞增）。
 * @property stack 該面內的零基底墩序號，從該面玩家視角的右端起算（`0` 為最右側的墩）。
 * @property layer 墩內的層數；`0` 為下層，`1` 為上層。
 */
data class TileWallPosition(
    val side: Int,
    val stack: Int,
    val layer: Int,
)
