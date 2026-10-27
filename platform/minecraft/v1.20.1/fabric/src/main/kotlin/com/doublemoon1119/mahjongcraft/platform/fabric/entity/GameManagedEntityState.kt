package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import kotlin.uuid.Uuid

/**
 * Entity 是否由牌局管理，以及它所屬的桌子。
 *
 * @property managedByGame 是否由牌局生命週期管理。
 * @property tableId 保存的桌子 UUID；欄位缺少或格式無效時為 `null`，不改變管理旗標。
 */
data class GameManagedEntityState(
    val managedByGame: Boolean,
    val tableId: Uuid?,
)
