package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/** 指定觀看者可見的開局準備狀態。 */
data class RoundPreparationSnapshot(
    val stepId: String,
    val stepIndex: Int,
    val participantPlayerIds: Set<Uuid>,
    val completedPlayerIds: Set<Uuid>,
    val ownInputSpec: RoundPreparationInputSpec?,
    val ownSubmission: RoundPreparationSubmission?,
)
