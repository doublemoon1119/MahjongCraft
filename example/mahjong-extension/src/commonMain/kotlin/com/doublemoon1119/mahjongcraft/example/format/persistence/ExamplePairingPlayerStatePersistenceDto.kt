package com.doublemoon1119.mahjongcraft.example.format.persistence

import kotlinx.serialization.Serializable

/**
 * 玩家本局攤牌標記的保存格式。
 *
 * @property handRevealed 本局是否已攤牌。
 */
@Serializable
data class ExamplePairingPlayerStatePersistenceDto(
    val handRevealed: Boolean,
)
