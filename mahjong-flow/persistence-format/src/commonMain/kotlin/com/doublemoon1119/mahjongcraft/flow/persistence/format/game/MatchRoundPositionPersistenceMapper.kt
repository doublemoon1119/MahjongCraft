package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPhase
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 將權威局位轉成 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun MatchRoundPosition.toPersistenceDto(): MatchRoundPositionPersistenceDto = MatchRoundPositionPersistenceDto(
    sequenceIndex = sequenceIndex,
    prevalentWind = WindPersistenceDto.valueOf(prevalentWind.name),
    localRoundNumber = localRoundNumber,
    phase = MatchRoundPhasePersistenceDto.valueOf(phase.name),
)

/**
 * 將 persistence DTO 還原成權威局位。
 *
 * @return 轉換後的結果。
 */
fun MatchRoundPositionPersistenceDto.toDomain(): MatchRoundPosition = MatchRoundPosition(
    sequenceIndex = sequenceIndex,
    prevalentWind = Wind.valueOf(prevalentWind.name),
    localRoundNumber = localRoundNumber,
    phase = MatchRoundPhase.valueOf(phase.name),
)
