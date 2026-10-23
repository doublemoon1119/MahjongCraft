package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import kotlinx.serialization.Serializable

/**
 * [RuleDecisionConfig] 的網路 DTO。
 *
 * @property baseSeconds 每次玩家規則選擇可使用的基本秒數。
 */
@Serializable
data class RuleDecisionConfigDto(
    val baseSeconds: Int,
)
