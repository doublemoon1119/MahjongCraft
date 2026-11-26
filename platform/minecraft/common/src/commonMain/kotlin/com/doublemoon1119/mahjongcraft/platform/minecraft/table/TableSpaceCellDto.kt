package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlinx.serialization.Serializable

/**
 * 擺牌層一格的世界方塊座標。
 *
 * @property x 方塊 x 座標。
 * @property y 方塊 y 座標。
 * @property z 方塊 z 座標。
 */
@Serializable
data class TableSpaceCellDto(
    val x: Int,
    val y: Int,
    val z: Int,
)
