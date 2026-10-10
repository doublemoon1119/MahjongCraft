package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPhase
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPosition

/**
 * 將領域資料轉換為網路 DTO。
 * @return 轉換後的資料。
 */
fun MatchRoundPosition.toDto(): MatchRoundPositionDto = MatchRoundPositionDto(
    sequenceIndex = sequenceIndex,
    prevalentWind = prevalentWind.toDto(),
    localRoundNumber = localRoundNumber,
    phase = MatchRoundPhaseDto.valueOf(phase.name),
)

/**
 * 將網路 DTO 還原成權威局位。
 *
 * @return 轉換後的資料。
 */
fun MatchRoundPositionDto.toDomain(): MatchRoundPosition = MatchRoundPosition(
    sequenceIndex = sequenceIndex,
    prevalentWind = prevalentWind.toDomain(),
    localRoundNumber = localRoundNumber,
    phase = MatchRoundPhase.valueOf(phase.name),
)
