package com.doublemoon1119.mahjongcraft.flow.server.game.model

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis

/**
 * Flow 對指定玩家目前決策所提供的完整規則查詢結果。
 *
 * @property actions 目前合法的特殊動作及其選牌需求與分析。
 * @property discardAnalyses 自己回合的一般捨牌分析；不適用或規則未提供分析器時為空。
 * @property referenceTile 觸發目前決策的牌；沒有特定觸發牌時為 null。
 */
data class PlayerDecisionOptions(
    val actions: List<PlayerDecisionActionOption>,
    val discardAnalyses: List<DiscardReadinessAnalysis>,
    val referenceTile: Tile?,
)
