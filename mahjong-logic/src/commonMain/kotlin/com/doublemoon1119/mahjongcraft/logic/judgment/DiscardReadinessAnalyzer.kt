package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.visibleTiles

/**
 * 分析目前手牌及捨牌後聽牌狀態的介面，供呼叫端取得等待牌、剩餘張數與和牌資格。
 *
 * 目前只有日麻有具體實作；規則模組透過
 * [MahjongRuleModule.createDiscardReadinessAnalyzer]
 * 提供，不支援此分析（例如尚未實作聽牌概念的規則）時回傳 null，不需要實作這個介面。
 *
 * 剩餘張數只扣除 `visibleTiles` 中的牌：呼叫端以這位玩家的桌況快照產生（見 [TableStateSnapshot.visibleTiles]），
 * 分析器本身不判斷哪些牌公開。
 */
interface DiscardReadinessAnalyzer {
    /**
     * 分析玩家目前手牌是否已經聽牌；不支援目前手牌分析的規則維持預設 `null`。
     *
     * @param tableState 目前的權威桌況。
     * @param player 欲分析的玩家。
     * @param visibleTiles 這位玩家看得到牌面的每一張實體牌。
     * @return 目前手牌的等待牌分析；無法判定或尚未聽牌時為 null。
     */
    fun analyzeCurrentHand(
        tableState: TableState,
        player: MahjongPlayer,
        visibleTiles: List<IdentifiedTile>,
    ): HandReadinessAnalysis? = null

    /**
     * 逐張立牌假想捨牌後分析聽牌狀態，只回傳打出後仍聽牌的候選。
     *
     * @param tableState 目前的權威桌況。
     * @param player 欲分析的玩家。
     * @param visibleTiles 這位玩家看得到牌面的每一張實體牌。
     * @return 依立牌順序排列的分析結果列表。
     */
    fun analyze(
        tableState: TableState,
        player: MahjongPlayer,
        visibleTiles: List<IdentifiedTile>,
    ): List<DiscardReadinessAnalysis>

    /**
     * 逐張候選投影指定動作成立後的捨牌分析；不需要動作專屬語意的規則沿用一般分析。
     *
     * @param action 玩家已明確選擇、但尚未完成其選牌階段的動作。
     * @param visibleTiles 這位玩家看得到牌面的每一張實體牌。
     * @param tableState 目前的權威桌況。
     * @param player 欲分析的玩家。
     * @return 依立牌順序排列的動作後捨牌分析。
     */
    fun analyzeForAction(
        tableState: TableState,
        player: MahjongPlayer,
        action: GameAction,
        visibleTiles: List<IdentifiedTile>,
    ): List<DiscardReadinessAnalysis> = analyze(tableState = tableState, player = player, visibleTiles = visibleTiles)
}
