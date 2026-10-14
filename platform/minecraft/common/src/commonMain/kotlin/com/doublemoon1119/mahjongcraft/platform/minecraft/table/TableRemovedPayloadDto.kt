package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlinx.serialization.Serializable

/**
 * 桌子已從目前場地移除時使用的 S2C payload。
 *
 * 這與 `SnapshotClearedPayloadDto` 不同；後者只表示房間或對局快照不再存在，
 * 不代表世界中的桌子本身已被移除。
 *
 * @property tableId 已移除桌子的 UUID 字串。
 */
@Serializable
data class TableRemovedPayloadDto(
    val tableId: String,
)
