package com.doublemoon1119.mahjongcraft.example.format.persistence

import kotlinx.serialization.Serializable

/**
 * 玩家本局公開宣告標記的保存格式。
 *
 * @property declared 本局是否已完成示範宣告。
 */
@Serializable
data class ExamplePairingPlayerStatePersistenceDto(
    val declared: Boolean,
)
