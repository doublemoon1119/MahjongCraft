package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis
import com.doublemoon1119.mahjongcraft.logic.judgment.TileSelectionRequirement

/**
 * `action` 指令的合法動作候選項目。
 *
 * @property action 選中後實際要送出的完整動作。
 * @property token 玩家實際輸入的字面值。
 * @property referenceTile 該動作涉及的牌面；不涉及特定牌面的動作為 null。
 * @property tileSelectionRequirement 動作提交前需要額外選取的手牌契約；不需選牌時為 null。
 * @property discardAnalyses 該動作選牌的捨牌分析；不適用時為空。
 */
data class GameActionCandidate(
    val action: GameAction,
    val token: String,
    val referenceTile: Tile?,
    val tileSelectionRequirement: TileSelectionRequirement?,
    val discardAnalyses: List<DiscardReadinessAnalysis>,
)
