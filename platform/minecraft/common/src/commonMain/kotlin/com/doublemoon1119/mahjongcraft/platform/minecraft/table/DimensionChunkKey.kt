package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 反向查詢單一 dimension chunk 的索引 key。
 *
 * @property dimensionId 完整 dimension registry identifier。
 * @property chunkX Chunk X 座標。
 * @property chunkZ Chunk Z 座標。
 */
data class DimensionChunkKey(
    val dimensionId: String,
    val chunkX: Int,
    val chunkZ: Int,
)
