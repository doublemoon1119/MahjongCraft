package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.WindDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPhase
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import kotlinx.serialization.Serializable

/**
 * 權威局位的網路 DTO。
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

/** 權威局位階段的網路 DTO。 */
@Serializable
enum class MatchRoundPhaseDto {
    /** 原定賽程。 */
    REGULAR,

    /** 規則追加的延長賽程。 */
    EXTRA,
}

/** 將權威局位轉成網路 DTO。 */
fun MatchRoundPosition.toDto(): MatchRoundPositionDto = MatchRoundPositionDto(
    sequenceIndex = sequenceIndex,
    prevalentWind = prevalentWind.toDto(),
    localRoundNumber = localRoundNumber,
    phase = MatchRoundPhaseDto.valueOf(phase.name),
)

/** 將網路 DTO 還原成權威局位。 */
fun MatchRoundPositionDto.toDomain(): MatchRoundPosition = MatchRoundPosition(
    sequenceIndex = sequenceIndex,
    prevalentWind = prevalentWind.toDomain(),
    localRoundNumber = localRoundNumber,
    phase = MatchRoundPhase.valueOf(phase.name),
)
