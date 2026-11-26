package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun RuleDecisionSnapshot.toDto(): RuleDecisionSnapshotDto = RuleDecisionSnapshotDto(
    stepId = stepId,
    stepIndex = stepIndex,
    ownInputSpec = ownInputSpec?.toDto(),
    ownSubmission = ownSubmission?.toDto(),
)

/**
 * 將網路資料還原為規則選擇快照。
 *
 * @return 轉換後的資料。
 */
fun RuleDecisionSnapshotDto.toDomain(): RuleDecisionSnapshot = RuleDecisionSnapshot(
    stepId = stepId,
    stepIndex = stepIndex,
    ownInputSpec = ownInputSpec?.toDomain(),
    ownSubmission = ownSubmission?.toDomain(),
)
