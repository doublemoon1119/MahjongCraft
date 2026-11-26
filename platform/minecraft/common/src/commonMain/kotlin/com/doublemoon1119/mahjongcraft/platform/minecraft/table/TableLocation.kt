package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * Minecraft 世界內麻將桌的版本無關位置。
 *
 * @property dimensionId 完整 dimension registry identifier，例如 `minecraft:overworld`。
 * @property x 方塊 X 座標。
 * @property y 方塊 Y 座標。
 * @property z 方塊 Z 座標。
 */
data class TableLocation(
    val dimensionId: String,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    init {
        MinecraftResourceIds.requireValid(dimensionId) { "Invalid dimension identifier: $dimensionId" }
    }

    /** 所在 chunk 的 X 座標。 */
    val chunkX: Int get() = x shr CHUNK_COORDINATE_SHIFT

    /** 所在 chunk 的 Z 座標。 */
    val chunkZ: Int get() = z shr CHUNK_COORDINATE_SHIFT

    /** [TableLocation] 使用的固定數值。 */
    private companion object {
        /** 方塊座標轉換為 16×16 chunk 座標的位移量。 */
        const val CHUNK_COORDINATE_SHIFT: Int = 4
    }
}
