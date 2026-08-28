package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RoundPreparationInputSpecDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RoundPreparationSubmissionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** 指定觀看者可見的開局準備網路快照。 */
@Serializable
data class RoundPreparationSnapshotDto(
    val stepId: String,
    val stepIndex: Int,
    val participantPlayerIds: Set<String>,
    val completedPlayerIds: Set<String>,
    val ownInputSpec: RoundPreparationInputSpecDto?,
    val ownSubmission: RoundPreparationSubmissionDto?,
)

/** 將開局準備快照轉為網路資料。 */
fun RoundPreparationSnapshot.toDto(): RoundPreparationSnapshotDto = RoundPreparationSnapshotDto(
    stepId = stepId,
    stepIndex = stepIndex,
    participantPlayerIds = participantPlayerIds.mapTo(mutableSetOf(), Uuid::toString),
    completedPlayerIds = completedPlayerIds.mapTo(mutableSetOf(), Uuid::toString),
    ownInputSpec = ownInputSpec?.toDto(),
    ownSubmission = ownSubmission?.toDto(),
)

/** 將網路資料還原為開局準備快照。 */
fun RoundPreparationSnapshotDto.toDomain(): RoundPreparationSnapshot = RoundPreparationSnapshot(
    stepId = stepId,
    stepIndex = stepIndex,
    participantPlayerIds = participantPlayerIds.mapTo(mutableSetOf(), Uuid::parse),
    completedPlayerIds = completedPlayerIds.mapTo(mutableSetOf(), Uuid::parse),
    ownInputSpec = ownInputSpec?.toDomain(),
    ownSubmission = ownSubmission?.toDomain(),
)
