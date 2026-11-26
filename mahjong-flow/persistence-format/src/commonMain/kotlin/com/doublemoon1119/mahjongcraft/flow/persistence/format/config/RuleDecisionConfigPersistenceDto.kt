package com.doublemoon1119.mahjongcraft.flow.persistence.format.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * [RuleDecisionConfig] 的 persistence DTO。
 *
 * @property base 每次玩家規則選擇可使用的基本時間。
 */
@Serializable
data class RuleDecisionConfigPersistenceDto(
    val base: Duration,
)
