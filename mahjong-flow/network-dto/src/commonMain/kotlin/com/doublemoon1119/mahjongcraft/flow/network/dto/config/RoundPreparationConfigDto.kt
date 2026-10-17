package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig
import kotlinx.serialization.Serializable

/**
 * [RoundPreparationConfig] 的網路 DTO。
 *
 * @property baseSeconds 每次玩家準備決策可使用的基本秒數。
 */
@Serializable
data class RoundPreparationConfigDto(
    val baseSeconds: Int,
)
