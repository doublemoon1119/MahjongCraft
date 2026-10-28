package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.example.format.ExampleFormatIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.PlayerRuleStateDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 玩家本局攤牌標記的網路格式。
 *
 * @property handRevealed 本局是否已攤牌。
 */
@Serializable
@SerialName(ExampleFormatIds.PLAYER_STATE)
data class ExamplePairingPlayerStateDto(
    val handRevealed: Boolean,
) : PlayerRuleStateDto
