package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.DecisionTimeoutPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import kotlinx.serialization.Serializable

/**
 * [GameFlowConfig] 的網路 DTO。
 *
 * @property timeControl 遊戲動作的時間控制設定。
 * @property ruleDecision 規則選擇設定；不需要規則選擇時為 null。
 * @property decisionTimeoutPolicy 決策逾時時採用的處理政策。
 * @property spectatingPolicy 觀戰政策。
 * @property spectatorHandVisibility 觀戰者可見手牌的政策。
 */
@Serializable
data class GameFlowConfigDto(
    val timeControl: ActionTimeControlDto,
    val ruleDecision: RuleDecisionConfigDto?,
    val decisionTimeoutPolicy: DecisionTimeoutPolicy,
    val spectatingPolicy: SpectatingPolicy,
    val spectatorHandVisibility: SpectatorHandVisibility,
)
