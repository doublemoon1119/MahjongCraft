package com.doublemoon1119.mahjongcraft.model

import java.util.*

/**
 * 具有唯一身份標識的麻將牌。
 *
 * 用於將領域邏輯層的 [Tile] 與外部系統（如 Minecraft Entity UUID）進行對接。
 * 藉由組合 (Composition) 而非繼承的方式，保持了 Tile 屬性的純粹性。
 *
 * @property id 唯一識別碼。在 Minecraft 環境中即為 Entity 的 UUID。
 * @property tile 該張牌的物理種類與屬性。
 */
data class IdentifiedTile(
    val id: UUID,
    val tile: Tile
)