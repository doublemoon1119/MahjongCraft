package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/**
 * 一次權威決策計時同步的剩餘時間。
 *
 * @property phase 目前決策階段。
 * @property baseRemainingMillis 尚未使用的基本思考時間。
 * @property reserveRemainingMillis 尚未使用的保留思考時間。
 * @property prompt 目前有效的決策提示。
 */
@Serializable
data class DecisionTimerStatusDto(
    val phase: PlayerDecisionPhaseDto,
    val baseRemainingMillis: Long,
    val reserveRemainingMillis: Long,
    val prompt: PlayerDecisionPromptDto?,
)
