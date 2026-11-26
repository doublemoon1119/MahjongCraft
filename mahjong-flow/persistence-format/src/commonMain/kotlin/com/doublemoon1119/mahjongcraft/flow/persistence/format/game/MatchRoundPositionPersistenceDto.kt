package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * 權威局位的 persistence DTO。
 *
 * @property sequenceIndex 整場賽程中從零開始的單調序號。
 * @property prevalentWind 此局權威場風。
 * @property localRoundNumber 此場風內從一開始的局數。
 * @property phase 原定賽程或延長賽階段。
 */
@Serializable
data class MatchRoundPositionPersistenceDto(
    val sequenceIndex: Int,
    val prevalentWind: WindPersistenceDto,
    val localRoundNumber: Int,
    val phase: MatchRoundPhasePersistenceDto,
)
