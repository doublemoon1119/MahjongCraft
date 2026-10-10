package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ScoreConfigDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import kotlinx.serialization.Serializable

/**
 * [RiichiScoreConfig] 的網路 DTO。
 *
 * @property initialScore 對局開始時的初始分數。
 * @property bustThreshold 被飛條件的分數門檻；未設定時為 `null`。
 * @property minPointsToWin 和牌所需的最低點數。
 * @property notenPenaltyUnit 流局未聽牌罰符的單位分數。
 */
@Serializable
data class RiichiScoreConfigDto(
    val initialScore: Int,
    val bustThreshold: Int?,
    val minPointsToWin: Int,
    val notenPenaltyUnit: Int,
) : ScoreConfigDto
