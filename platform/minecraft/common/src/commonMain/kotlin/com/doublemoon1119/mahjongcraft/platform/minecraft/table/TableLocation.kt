package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/** Minecraft 世界內麻將桌的版本無關位置。 */
data class TableLocation(
    /** 完整 dimension registry identifier，例如 `minecraft:overworld`。 */
    val dimensionId: String,
    /** 方塊 X 座標。 */
    val x: Int,
    /** 方塊 Y 座標。 */
    val y: Int,
    /** 方塊 Z 座標。 */
    val z: Int,
) {
    init {
        require(DIMENSION_ID_PATTERN.matches(dimensionId)) { "Invalid dimension identifier: $dimensionId" }
    }

    /** 所在 chunk 的 X 座標。 */
    val chunkX: Int get() = x shr CHUNK_COORDINATE_SHIFT

    /** 所在 chunk 的 Z 座標。 */
    val chunkZ: Int get() = z shr CHUNK_COORDINATE_SHIFT

    /** [TableLocation] 使用的固定格式。 */
    private companion object {
        /** Minecraft namespaced identifier 的基本格式。 */
        val DIMENSION_ID_PATTERN: Regex = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")

        /** 方塊座標轉換為 16×16 chunk 座標的位移量。 */
        const val CHUNK_COORDINATE_SHIFT: Int = 4
    }
}

/** 已知麻將桌位置與防止延遲工作誤用舊資料的 revision。 */
data class TableLocationEntry(
    /** 麻將桌穩定 UUID。 */
    val tableId: Uuid,
    /** 麻將桌最後確認的位置。 */
    val location: TableLocation,
    /** 每次同一桌位置改變時遞增的版本。 */
    val revision: Long,
) {
    init {
        require(revision > 0) { "Table location revision must be positive" }
    }
}

/** 反向查詢單一 dimension chunk 的索引 key。 */
data class DimensionChunkKey(
    /** 完整 dimension registry identifier。 */
    val dimensionId: String,
    /** Chunk X 座標。 */
    val chunkX: Int,
    /** Chunk Z 座標。 */
    val chunkZ: Int,
)
