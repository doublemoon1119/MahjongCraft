package com.doublemoon1119.mahjongcraft.example.format.network

import com.doublemoon1119.mahjongcraft.example.format.ExampleFormatIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ScoreConfigDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 初始分數與擊飛設定的網路格式。
 *
 * @property initialScore 每位玩家的初始分數。
 * @property bustThreshold 擊飛門檻；不使用時為 null。
 */
@Serializable
@SerialName(ExampleFormatIds.SCORE_CONFIG)
data class ExamplePairingScoreConfigDto(
    val initialScore: Int,
    val bustThreshold: Int?,
) : ScoreConfigDto
