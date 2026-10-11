package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun RoundPreparationSnapshot.toDto(): RoundPreparationSnapshotDto = RoundPreparationSnapshotDto(
    stepId = stepId,
    stepIndex = stepIndex,
    participantPlayerIds = participantPlayerIds.mapTo(mutableSetOf(), Uuid::toString),
    completedPlayerIds = completedPlayerIds.mapTo(mutableSetOf(), Uuid::toString),
    ownInputSpec = ownInputSpec?.toDto(),
    ownSubmission = ownSubmission?.toDto(),
)

/**
 * 將網路資料還原為開局準備快照。
 *
 * @return 轉換後的資料。
 */
fun RoundPreparationSnapshotDto.toDomain(): RoundPreparationSnapshot = RoundPreparationSnapshot(
    stepId = stepId,
    stepIndex = stepIndex,
    participantPlayerIds = participantPlayerIds.mapTo(mutableSetOf(), Uuid::parse),
    completedPlayerIds = completedPlayerIds.mapTo(mutableSetOf(), Uuid::parse),
    ownInputSpec = ownInputSpec?.toDomain(),
    ownSubmission = ownSubmission?.toDomain(),
)
