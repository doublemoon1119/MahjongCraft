package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import kotlinx.serialization.Serializable

/**
 * 額外牌面授權的權威保存格式，不能直接作為觀察者資料。
 *
 * @property kind NONE、PUBLIC 或 PLAYERS，按文字值保存。
 * @property playerIds 僅 PLAYERS 可以保存的本局玩家 UUID。
 */
@Serializable
data class TileFaceAccessPersistenceDto(
    val kind: String,
    val playerIds: Set<String>,
)
