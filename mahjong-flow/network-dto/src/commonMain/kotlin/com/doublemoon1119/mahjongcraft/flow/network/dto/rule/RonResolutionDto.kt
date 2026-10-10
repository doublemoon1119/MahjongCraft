package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import kotlinx.serialization.Serializable

/** [RonResolution] 的網路 DTO。 */
@Serializable
enum class RonResolutionDto { NEAREST_WINNER, ALL_WINNERS, ABORTIVE_DRAW }
