package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import kotlinx.serialization.Serializable

/**
 * [MultiRonPolicy] 的網路 DTO。
 *
 * @property doubleRonResolution 兩名玩家榮和時的處理方式。
 * @property tripleRonResolution 三名玩家榮和時的處理方式。
 */
@Serializable
data class MultiRonPolicyDto(
    val doubleRonResolution: RonResolutionDto,
    val tripleRonResolution: RonResolutionDto,
)
