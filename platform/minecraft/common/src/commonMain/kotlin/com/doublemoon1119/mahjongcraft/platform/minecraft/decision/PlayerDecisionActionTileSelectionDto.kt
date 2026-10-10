package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import kotlinx.serialization.Serializable

/**
 * 動作候選需要額外選牌時的合法牌張與分析。
 *
 * @property eligibleTileIds 可選的實體牌 ID。
 * @property minCount 最少選牌數量。
 * @property maxCount 最多選牌數量。
 * @property discardAnalyses 選擇該動作後適用的捨牌分析。
 */
@Serializable
data class PlayerDecisionActionTileSelectionDto(
    val eligibleTileIds: List<String>,
    val minCount: Int,
    val maxCount: Int,
    val discardAnalyses: List<DiscardReadinessAnalysisDto>,
)
