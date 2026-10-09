package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
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
 * 每位玩家在 [current] 中、相對 [previous] 新增到 `actionHistory` 的動作；[previous] 中沒有的玩家視為全部新增。
 *
 * 進入新的一局時 `actionHistory` 從頭記錄，沒有新增的動作。
 *
 * @param previous 交易或命令之前的桌況。
 * @param current 交易或命令之後的桌況。
 * @return 以玩家 UUID 索引的新增動作，依記錄順序排列。
 */
fun newlyRecordedActionsByPlayerId(previous: TableState, current: TableState): Map<Uuid, List<GameAction>> = current.players.associate { player ->
    val previousActionCount = previous.players.firstOrNull { it.id == player.id }?.actionHistory?.size ?: 0
    player.id to player.actionHistory.drop(previousActionCount)
}

/**
 * 由新增的流局記錄建立本局結算摘要，供規則沒有在流局交易中寫入摘要時使用。
 *
 * 所有玩家都記錄流局時為途中流局：沒有得利者，莊家連莊。否則為荒牌流局：記錄流局的玩家為得利者，莊家在其中時連莊。
 *
 * @param state 記錄流局之後的桌況。
 * @param newActionsByPlayerId 這次新增的動作，見 [newlyRecordedActionsByPlayerId]。
 * @return 本局結算摘要；沒有新增流局記錄時為 null。
 */
fun recordedDrawCompletion(state: TableState, newActionsByPlayerId: Map<Uuid, List<GameAction>>): RoundCompletionSummary? {
    val reason = newActionsByPlayerId.values.asSequence()
        .flatten()
        .filterIsInstance<GameAction.ExhaustiveDraw>()
        .firstOrNull()
        ?.reason
        ?: return null
    val affectedPlayerIds = newActionsByPlayerId.filterValues { actions -> actions.any { it is GameAction.ExhaustiveDraw } }.keys
    val isAbortive = affectedPlayerIds.size == state.playerCount
    val directive = if (isAbortive || state.dealerPlayerId in affectedPlayerIds) {
        RoundTransitionDirective.REPEAT_DEALER
    } else {
        RoundTransitionDirective.ADVANCE_DEALER
    }
    return RoundCompletionSummary(
        outcomeId = reason.id,
        classification = if (isAbortive) RoundCompletionClassification.ABORTIVE_DRAW else RoundCompletionClassification.EXHAUSTIVE_DRAW,
        beneficiaryPlayerIds = if (isAbortive) emptySet() else affectedPlayerIds,
        transitionDirective = directive,
        settledScoresByPlayerId = state.players.associate { it.id to it.score },
    )
}
