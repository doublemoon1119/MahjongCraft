package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * 一次權威決策計時同步的剩餘時間。
 *
 * @property phase 目前決策階段。
 * @property baseRemaining 尚未使用的基本思考時間。
 * @property reserveRemaining 尚未使用的保留思考時間。
 * @property prompt 目前有效的決策提示。
 */
@Serializable
data class DecisionTimerStatusDto(
    val phase: PlayerDecisionPhaseDto,
    val baseRemaining: Duration,
    val reserveRemaining: Duration,
    val prompt: PlayerDecisionPromptDto?,
)
