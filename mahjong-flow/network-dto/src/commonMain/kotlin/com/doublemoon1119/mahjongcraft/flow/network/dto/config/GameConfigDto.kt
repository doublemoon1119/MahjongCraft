package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import kotlinx.serialization.Serializable

/**
 * [GameConfig] 的網路 DTO。
 *
 * @property ruleConfig 規則設定 DTO。
 * @property flowConfig 流程設定 DTO。
 */
@Serializable
data class GameConfigDto(
    val ruleConfig: MahjongRuleConfigDto,
    val flowConfig: GameFlowConfigDto,
)
