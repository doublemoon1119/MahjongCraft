package com.doublemoon1119.mahjongcraft.flow.server.game.model

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.TileSelectionRequirement

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

/**
 * 一個合法特殊動作及其完整規則選牌資訊。
 *
 * @property action 合法動作。
 * @property tileSelectionRequirement 動作成立前需要額外選牌時的契約；不需選牌時為 null。
 * @property discardAnalyses 該動作各候選牌的捨牌分析；不適用或規則未提供分析器時為空。
 */
data class PlayerDecisionActionOption(
    val action: GameAction,
    val tileSelectionRequirement: TileSelectionRequirement?,
    val discardAnalyses: List<DiscardReadinessAnalysis>,
)
