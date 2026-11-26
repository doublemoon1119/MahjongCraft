package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * [RuleDecisionConfig] 的網路 DTO。
 *
 * @property base 每次玩家規則選擇可使用的基本時間。
 */
@Serializable
data class RuleDecisionConfigDto(
    val base: Duration,
)
