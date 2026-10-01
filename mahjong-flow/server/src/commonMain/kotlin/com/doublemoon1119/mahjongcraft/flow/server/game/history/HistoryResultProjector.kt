package com.doublemoon1119.mahjongcraft.flow.server.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryResultSummary
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.RankablePlayer

/** 將已保存的終局事實投影為查詢可用的分數與名次，不建立第二套排名規則。 */
object HistoryResultProjector {
    /**
     * 從已保存事件重建最後一個可用桌況，並使用規則模組的終局比較器計算名次。
     *
     * @param events 同一場對局依序保存的權威事件。
     * @param moduleRegistry 已凍結的規則模組註冊表。
     * @return 玩家 UUID 對應的終局結果；若缺少終局事實則回傳空清單，若規則模組無法解析則名次為 null。
     */
    fun project(
        events: List<HistoryOutboxEvent>,
        moduleRegistry: MahjongModuleRegistry,
    ): List<HistoryResultSummary> {
        val opening = events.asSequence()
            .map { it.fact }
            .filterIsInstance<HistoryFact.MatchStarted>()
            .firstOrNull()
            ?: return emptyList()
        val completed = events.asSequence()
            .map { it.fact }
            .filterIsInstance<HistoryFact.MatchCompleted>()
            .lastOrNull()
            ?: return emptyList()
        val finalState = events.asSequence()
            .sortedBy { it.sequence }
            .map { it.fact }
            .fold(opening.tableState) { state, fact ->
                when (fact) {
                    is HistoryFact.RoundStarted -> fact.tableState
                    is HistoryFact.TableChanged -> when (val result = fact.result) {
                        is HistoryTableResult.Change -> result.change.applyTo(state)
                        is HistoryTableResult.Checkpoint -> result.tableState
                    }
                    else -> state
                }
            }
        val scoreByPlayer = completed.finalScoresByPlayerId
        val rankableByPlayerId = finalState.players.associate { player ->
            player.id to
                object : RankablePlayer {
                    /** 事件中的終局分數；缺漏時使用不可見的暫時值，且不會產生名次。 */
                    override val score: Int = scoreByPlayer[player.id] ?: 0

                    /** 桌況中該玩家的本局風位。 */
                    override val seatWind = player.seatWind

                    /** 桌況中該玩家固定的起家座位。 */
                    override val initialSeatIndex: Int = player.initialSeatIndex
                }
        }
        val rankByPlayerId = if (finalState.players.all { it.id in scoreByPlayer }) {
            runCatching {
                val module = moduleRegistry.getModule(finalState.config)
                val rankedIds = finalState.players
                    .sortedWith { left, right ->
                        module.compareForMatchRanking().compare(
                            rankableByPlayerId.getValue(left.id),
                            rankableByPlayerId.getValue(right.id),
                        )
                    }.map { it.id }
                rankedIds.mapIndexed { index, id -> id to index + 1 }.toMap()
            }.getOrNull()
        } else {
            null
        }
        return finalState.players.map { player ->
            HistoryResultSummary(
                playerId = player.id,
                finalScore = scoreByPlayer[player.id],
                finalRank = rankByPlayerId?.get(player.id),
            )
        }
    }
}
