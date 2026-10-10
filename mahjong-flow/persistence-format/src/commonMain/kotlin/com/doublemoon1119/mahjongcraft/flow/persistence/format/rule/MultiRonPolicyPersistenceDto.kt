package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import kotlinx.serialization.Serializable

/**
 * [MultiRonPolicy] 的 persistence DTO。
 *
 * @property doubleRonResolution 兩家榮和時的處理方式。
 * @property tripleRonResolution 三家榮和時的處理方式。
 */
@Serializable
data class MultiRonPolicyPersistenceDto(
    val doubleRonResolution: RonResolutionPersistenceDto,
    val tripleRonResolution: RonResolutionPersistenceDto,
)
