package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import kotlin.uuid.Uuid

/**
 * 權威狀態中的待寫歷史，獨立於可能被移除的 [Game]。
 *
 * @property nextSequenceByMatchId 每場下一個可指派的序號，從 1 開始；即使事件遺失也會推進。
 * @property pendingEvents 尚待寫入歷史儲存系統的事件。
 * @property firstMissingSequenceByMatchId 每場最早遺失的序號；容量不足或記錄失敗時供診斷使用。
 * @property decisionsByMatchId 每場固定的記錄資格與停止原因；與待寫事件一起持久化。
 * @property terminalByMatchId 已離開權威遊戲集合的場次終點證據。
 * @property transfersByMatchId 尚未完成的隔離歷史轉移；完成或確認中止前保護其事件與 metadata。
 */
data class HistoryRecordingState(
    val nextSequenceByMatchId: Map<Uuid, Long> = emptyMap(),
    val pendingEvents: List<HistoryOutboxEvent> = emptyList(),
    val firstMissingSequenceByMatchId: Map<Uuid, Long> = emptyMap(),
    val decisionsByMatchId: Map<Uuid, HistoryRecordingDecision> = emptyMap(),
    val terminalByMatchId: Map<Uuid, HistoryRecordingTerminal> = emptyMap(),
    val transfersByMatchId: Map<Uuid, HistoryRecordingTransfer> = emptyMap(),
) {
    init {
        require(nextSequenceByMatchId.values.all { it > 0L }) { "History sequence must be positive" }
        require(firstMissingSequenceByMatchId.values.all { it > 0L }) { "Missing history sequence must be positive" }
        require(pendingEvents.distinctBy { it.matchId to it.sequence }.size == pendingEvents.size) {
            "Pending history event IDs must be unique"
        }
    }

    /**
     * 為同一權威交易的事件依序指派穩定序號；容量耗盡時只記缺口，不阻塞對局。
     *
     * @param game 事件所屬場次與當前局數的權威狀態。
     * @param drafts 按提交順序排列、尚未指派序號的事件。
     * @param occurredAtEpochMillis 本次交易的 UTC 毫秒時間戳，不用於事件排序。
     * @param maxPendingEvents 全部場次共用的待寫事件容量上限。
     * @return 加入事件並推進序號後的新狀態。
     */
    fun append(
        game: Game,
        drafts: List<HistoryEventDraft>,
        occurredAtEpochMillis: Long,
        maxPendingEvents: Int,
    ): HistoryRecordingState {
        require(maxPendingEvents >= 0) { "Pending history capacity must not be negative" }
        if (drafts.isEmpty()) return this
        val nextSequence = nextSequenceByMatchId[game.matchId] ?: 1L
        require(nextSequence <= Long.MAX_VALUE - drafts.size) { "History sequence exhausted" }
        val remainingCapacity = (maxPendingEvents - pendingEvents.size).coerceAtLeast(0)
        val acceptedDrafts = drafts.take(remainingCapacity)
        val appended = acceptedDrafts.mapIndexed { index, draft ->
            HistoryOutboxEvent(
                matchId = game.matchId,
                tableId = game.id,
                roundNumber = game.tableState.roundNumber,
                sequence = nextSequence + index,
                transactionFirstSequence = nextSequence,
                occurredAtEpochMillis = occurredAtEpochMillis,
                actorPlayerId = draft.actorPlayerId,
                fact = draft.fact,
            )
        }
        val firstMissing = if (acceptedDrafts.size < drafts.size) nextSequence + acceptedDrafts.size else null
        return copy(
            nextSequenceByMatchId = nextSequenceByMatchId + (game.matchId to nextSequence + drafts.size),
            pendingEvents = pendingEvents + appended,
            firstMissingSequenceByMatchId = if (firstMissing == null) {
                firstMissingSequenceByMatchId
            } else {
                firstMissingSequenceByMatchId + (
                    game.matchId to minOf(firstMissingSequenceByMatchId[game.matchId] ?: firstMissing, firstMissing)
                    )
            },
        )
    }

    /**
     * 記錄失敗時為該場保留一個可診斷的序號缺口，不以空白事件冒充成功。
     *
     * @param game 記錄失敗時仍已成功提交的遊戲狀態。
     * @return 保留最早缺口並推進下一序號後的新狀態。
     */
    fun recordMissing(game: Game): HistoryRecordingState {
        val nextSequence = nextSequenceByMatchId[game.matchId] ?: 1L
        if (nextSequence == Long.MAX_VALUE) return this
        return copy(
            nextSequenceByMatchId = nextSequenceByMatchId + (game.matchId to nextSequence + 1L),
            firstMissingSequenceByMatchId = firstMissingSequenceByMatchId + (
                game.matchId to minOf(firstMissingSequenceByMatchId[game.matchId] ?: nextSequence, nextSequence)
                ),
        )
    }
}
