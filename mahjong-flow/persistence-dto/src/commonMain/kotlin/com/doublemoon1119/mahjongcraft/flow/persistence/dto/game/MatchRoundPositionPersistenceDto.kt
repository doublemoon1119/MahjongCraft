package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPhase
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.Wind
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

/** 權威局位階段的 persistence DTO。 */
@Serializable
enum class MatchRoundPhasePersistenceDto {
    /** 原定賽程。 */
    REGULAR,

    /** 規則追加的延長賽程。 */
    EXTRA,
}

/** 將權威局位轉成 persistence DTO。 */
fun MatchRoundPosition.toPersistenceDto(): MatchRoundPositionPersistenceDto = MatchRoundPositionPersistenceDto(
    sequenceIndex = sequenceIndex,
    prevalentWind = WindPersistenceDto.valueOf(prevalentWind.name),
    localRoundNumber = localRoundNumber,
    phase = MatchRoundPhasePersistenceDto.valueOf(phase.name),
)

/** 將 persistence DTO 還原成權威局位。 */
fun MatchRoundPositionPersistenceDto.toDomain(): MatchRoundPosition = MatchRoundPosition(
    sequenceIndex = sequenceIndex,
    prevalentWind = Wind.valueOf(prevalentWind.name),
    localRoundNumber = localRoundNumber,
    phase = MatchRoundPhase.valueOf(phase.name),
)
