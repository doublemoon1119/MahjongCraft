package com.doublemoon1119.mahjongcraft.flow.network.dto.config

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * [ActionTimeControl] 的網路 DTO。
 *
 * @property base 基本思考時間。
 * @property reserve 保留思考時間。
 */
@Serializable
data class ActionTimeControlDto(
    val base: Duration,
    val reserve: Duration,
)
