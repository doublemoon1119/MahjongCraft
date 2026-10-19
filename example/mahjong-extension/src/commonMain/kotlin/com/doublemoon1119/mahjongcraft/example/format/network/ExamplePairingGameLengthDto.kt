package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.example.format.ExampleFormatIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 固定局數的網路格式。
 *
 * @property totalRounds 整場預定局數。
 */
@Serializable
@SerialName(ExampleFormatIds.GAME_LENGTH)
data class ExamplePairingGameLengthDto(
    val totalRounds: Int,
) : GameLengthDto
