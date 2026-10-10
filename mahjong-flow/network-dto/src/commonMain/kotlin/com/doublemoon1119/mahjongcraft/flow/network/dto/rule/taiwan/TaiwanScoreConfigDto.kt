package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ScoreConfigDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanScoreConfig
import kotlinx.serialization.Serializable

/**
 * [TaiwanScoreConfig] 的網路 DTO。
 *
 * @property baseScore 基本底分。
 * @property pointPerTai 每台對應的分數。
 * @property initialScore 對局開始時的初始分數。
 * @property bustThreshold 被飛條件的分數門檻；未設定時為 `null`。
 */
@Serializable
data class TaiwanScoreConfigDto(
    val baseScore: Int,
    val pointPerTai: Int,
    val initialScore: Int,
    val bustThreshold: Int?,
) : ScoreConfigDto
