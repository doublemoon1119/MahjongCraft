package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundConclusion
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import kotlin.uuid.Uuid

/**
 * 依規則的回合排名比較器建立從一開始的名次。
 *
 * @param state 要排名的桌況。
 * @param module 提供回合排名比較器的規則模組。
 * @return 以玩家 UUID 索引的名次。
 */
fun roundRanksByPlayer(state: TableState, module: MahjongRuleModule<*>): Map<Uuid, Int> = state.players
    .sortedWith(module.compareForRoundRanking())
    .withIndex()
    .associate { (index, player) -> player.id to index + 1 }

/**
 * 這一局以流局結束時的純結果資料，取自「本局已結束」階段記錄的結束方式；不是流局或本局尚未結束時為 null。
 *
 * 荒牌流局的得利者是流局時聽牌的玩家；途中流局沒有得利者。
 *
 * @receiver 本局結束後的桌況。
 * @return 流局的純結果資料。
 */
fun TableState.concludedDrawOutcome(): RecordedDrawOutcome? = when (val conclusion = (turnPhase as? TurnPhase.RoundOver)?.conclusion) {
    is RoundConclusion.ExhaustiveDraw -> RecordedDrawOutcome(conclusion.reason, RoundCompletionClassification.EXHAUSTIVE_DRAW, conclusion.tenpaiPlayerIds)
    is RoundConclusion.AbortiveDraw -> RecordedDrawOutcome(conclusion.reason, RoundCompletionClassification.ABORTIVE_DRAW, emptySet())
    is RoundConclusion.Win, null -> null
}
