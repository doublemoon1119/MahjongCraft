package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiWinAvailability
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 分析捨牌後聽牌狀態的介面，供呈現層顯示打某張牌之後的等待牌與風險，讓玩家判斷該不該打這張牌。
 *
 * 目前只有日麻有具體實作；規則模組透過
 * [com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule.createDiscardReadinessAnalyzer]
 * 提供，不支援此分析（例如尚未實作聽牌概念的規則）時回傳 null，不需要實作這個介面。
 */
interface DiscardReadinessAnalyzer {
    /**
     * 逐張立牌假想捨牌後分析聽牌狀態，只回傳打出後仍聽牌的候選。
     *
     * @param tableState 目前的權威桌況。
     * @param player 欲分析的玩家。
     * @return 依立牌順序排列的分析結果列表。
     */
    fun analyze(tableState: TableState, player: MahjongPlayer): List<DiscardReadinessAnalysis>
}

/** 假想捨出 [discardTileId] 後的聽牌分析結果。 */
data class DiscardReadinessAnalysis(
    val discardTileId: Uuid,
    val waitingTiles: List<WaitingTileAvailability>,
    val statusIndicatorId: String?,
)

/**
 * 一張等待牌的可用性。
 *
 * [winAvailability] 目前固定使用日麻的 [RiichiWinAvailability]，與 network-dto 的
 * `WaitingTileWinAvailabilityDto` 有同樣的設計限制——日後有規則模組需要不同和牌可用性語意時需一併
 * 重新設計，見該 DTO 的 KDoc／TODO。
 */
// TODO: 新增其他地區規則模組時重新評估 winAvailability 是否需要規則中立設計。
data class WaitingTileAvailability(
    val tile: Tile,
    val remainingCount: Int,
    val winAvailability: RiichiWinAvailability,
)
