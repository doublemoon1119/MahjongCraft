package com.doublemoon1119.mahjongcraft.flow.persistence.format.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig
import kotlinx.serialization.Serializable

/**
 * [RoundPreparationConfig] 的 persistence DTO。
 *
 * @property baseSeconds 每次玩家準備決策可使用的基本秒數。
 */
@Serializable
data class RoundPreparationConfigPersistenceDto(
    val baseSeconds: Int,
)
