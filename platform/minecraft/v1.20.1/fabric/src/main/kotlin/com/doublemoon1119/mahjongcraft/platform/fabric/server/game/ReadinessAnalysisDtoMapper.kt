package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HandReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.HandReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailability
import org.koin.core.annotation.Single

/** 將規則中立的手牌分析轉為私人同步資料。 */
@Single
class ReadinessAnalysisDtoMapper {
    /** 轉換一份假想捨牌後的分析。 */
    fun toDto(analysis: DiscardReadinessAnalysis): DiscardReadinessAnalysisDto = DiscardReadinessAnalysisDto(
        discardTileId = analysis.discardTileId.toString(),
        waitingTiles = analysis.waitingTiles.map(::toDto),
        statusIndicatorId = analysis.statusIndicatorId,
    )

    /** 轉換一份目前手牌分析。 */
    fun toDto(ruleModuleId: String, analysis: HandReadinessAnalysis): HandReadinessAnalysisDto = HandReadinessAnalysisDto(
        ruleModuleId = ruleModuleId,
        waitingTiles = analysis.waitingTiles.map(::toDto),
        statusIndicatorId = analysis.statusIndicatorId,
    )

    private fun toDto(availability: WaitingTileAvailability): WaitingTileAvailabilityDto = WaitingTileAvailabilityDto(
        tile = availability.tile.toDto(),
        remainingCount = availability.remainingCount,
        winAvailability = availability.winAvailability,
    )
}
