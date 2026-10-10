package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import kotlinx.serialization.Serializable

/**
 * 權威局位的網路 DTO。
 *
 *
 * @property sequenceIndex 整場賽程中從零開始的單調序號。
 * @property prevalentWind 此局權威場風。
 * @property localRoundNumber 此場風內從一開始的局數。
 * @property phase 原定賽程或延長賽階段。
 */
@Serializable
data class MatchRoundPositionDto(
    val sequenceIndex: Int,
    val prevalentWind: WindDto,
    val localRoundNumber: Int,
    val phase: MatchRoundPhaseDto,
)

/** 將權威局位轉成網路 DTO。 */
