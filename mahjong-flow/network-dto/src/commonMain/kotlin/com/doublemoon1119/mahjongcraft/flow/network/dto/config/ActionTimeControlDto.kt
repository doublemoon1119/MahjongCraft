package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import kotlinx.serialization.Serializable

/**
 * [ActionTimeControl] 的網路 DTO。
 *
 * @property baseSeconds 基本思考時間（秒）。
 * @property reserveSeconds 保留思考時間（秒）。
 */
@Serializable
data class ActionTimeControlDto(
    val baseSeconds: Int,
    val reserveSeconds: Int,
)
