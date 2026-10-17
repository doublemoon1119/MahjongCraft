package com.doublemoon1119.mahjongcraft.flow.persistence.format.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.DecisionTimeoutPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import kotlinx.serialization.Serializable

/**
 * [GameFlowConfig] 的完整 persistence DTO。
 *
 * @property baseSeconds 每個玩家每次行動可用的基礎秒數。
 * @property reserveSeconds 每個玩家可累積使用的保留秒數。
 * @property roundPreparation 局前準備設定；不需要局前準備時為 null。
 * @property decisionTimeoutPolicy 決策逾時時採用的處理政策。
 * @property spectatingPolicy 觀戰者的加入與觀看政策。
 * @property spectatorHandVisibility 觀戰者可見的手牌範圍。
 */
@Serializable
data class GameFlowConfigPersistenceDto(
    val baseSeconds: Int,
    val reserveSeconds: Int,
    val roundPreparation: RoundPreparationConfigPersistenceDto?,
    val decisionTimeoutPolicy: DecisionTimeoutPolicy,
    val spectatingPolicy: SpectatingPolicy,
    val spectatorHandVisibility: SpectatorHandVisibility,
)
