package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.example.format.ExampleFormatIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.PlayerRuleStateDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 玩家本局公開宣告標記的網路格式。
 *
 * @property declared 本局是否已完成示範宣告。
 */
@Serializable
@SerialName(ExampleFormatIds.PLAYER_STATE)
data class ExamplePairingPlayerStateDto(
    val declared: Boolean,
) : PlayerRuleStateDto
