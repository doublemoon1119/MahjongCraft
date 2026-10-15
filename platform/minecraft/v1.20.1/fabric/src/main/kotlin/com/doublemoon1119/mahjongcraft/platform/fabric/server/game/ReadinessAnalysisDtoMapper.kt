package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HandReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.HandReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailability

/** 將規則中立的手牌分析轉為私人同步資料。 */
class ReadinessAnalysisDtoMapper {
    /**
     * 轉換一份假想捨牌後的分析。
     *
     * @param analysis 捨牌後的等待牌分析。
     * @return 可傳送給該玩家的分析 DTO。
     */
    fun toDto(analysis: DiscardReadinessAnalysis): DiscardReadinessAnalysisDto = DiscardReadinessAnalysisDto(
        discardTileId = analysis.discardTileId.toString(),
        waitingTiles = analysis.waitingTiles.map(::toDto),
        statusIndicatorId = analysis.statusIndicatorId,
    )

    /**
     * 轉換一份目前手牌分析。
     *
     * @param ruleModuleId 對局採用的規則模組識別碼。
     * @param analysis 目前手牌的等待牌分析。
     * @return 可傳送給該玩家的分析 DTO。
     */
    fun toDto(ruleModuleId: String, analysis: HandReadinessAnalysis): HandReadinessAnalysisDto = HandReadinessAnalysisDto(
        ruleModuleId = ruleModuleId,
        waitingTiles = analysis.waitingTiles.map(::toDto),
        statusIndicatorId = analysis.statusIndicatorId,
    )

    /**
     * 將一種等待牌的可用狀態轉為同步 DTO。
     *
     * @param availability 一種等待牌及其可用張數、胡牌狀態。
     * @return 網路同步用的等待牌 DTO。
     */
    private fun toDto(availability: WaitingTileAvailability): WaitingTileAvailabilityDto = WaitingTileAvailabilityDto(
        tile = availability.tile.toDto(),
        remainingCount = availability.remainingCount,
        winAvailability = availability.winAvailability,
    )
}
