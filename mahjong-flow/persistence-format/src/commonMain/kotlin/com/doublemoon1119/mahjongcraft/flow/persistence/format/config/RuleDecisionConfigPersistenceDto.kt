package com.doublemoon1119.mahjongcraft.flow.persistence.format.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import kotlinx.serialization.Serializable

/**
 * [RuleDecisionConfig] 的 persistence DTO。
 *
 * @property baseSeconds 每次玩家規則選擇可使用的基本秒數。
 */
@Serializable
data class RuleDecisionConfigPersistenceDto(
    val baseSeconds: Int,
)
