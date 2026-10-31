package com.doublemoon1119.mahjongcraft.flow.persistence.format.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.DecisionTimeoutPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * [GameFlowConfig] 的完整 persistence DTO。
 *
 * @property base 每個玩家每次行動可用的基本思考時間。
 * @property reserve 每個玩家開局時取得的共用保留思考時間。
 * @property ruleDecision 規則選擇設定；不需要規則選擇時為 null。
 * @property decisionTimeoutPolicy 決策逾時時採用的處理政策。
 * @property spectatingPolicy 觀戰者的加入與觀看政策。
 * @property spectatorHandVisibility 觀戰者可見的手牌範圍。
 */
@Serializable
data class GameFlowConfigPersistenceDto(
    val base: Duration,
    val reserve: Duration,
    val ruleDecision: RuleDecisionConfigPersistenceDto?,
    val decisionTimeoutPolicy: DecisionTimeoutPolicy,
    val spectatingPolicy: SpectatingPolicy,
    val spectatorHandVisibility: SpectatorHandVisibility,
)
