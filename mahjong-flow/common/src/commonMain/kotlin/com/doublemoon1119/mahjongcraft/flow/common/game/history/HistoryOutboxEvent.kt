package com.doublemoon1119.mahjongcraft.flow.common.game.history

import kotlin.uuid.Uuid

/**
 * 保存於權威 outbox、供後續 writer 依場次與序號組成的穩定鍵重試的事件。
 *
 * @property matchId 此次開局的獨立場次 UUID，同一場地重開時不同。
 * @property venueId 場地 UUID，同一場地重開時保持不變。
 * @property roundNumber 此場次內的局數。
 * @property sequence 此場次內單調遞增的事件序號，從 1 開始。
 * @property transactionFirstSequence 同一權威交易內第一筆事件的序號。
 * @property occurredAtEpochMillis 事件提交時的 UTC 毫秒時間戳，不用於排序。
 * @property actorPlayerId 發起動作的玩家；系統或規則事件為 null。
 * @property fact 已提交的權威事實。
 */
data class HistoryOutboxEvent(
    val matchId: Uuid,
    val venueId: Uuid,
    val roundNumber: Int,
    val sequence: Long,
    val transactionFirstSequence: Long = sequence,
    val occurredAtEpochMillis: Long,
    val actorPlayerId: Uuid?,
    val fact: HistoryFact,
) {
    init {
        require(sequence > 0L) { "History sequence must be positive" }
        require(transactionFirstSequence in 1L..sequence) { "History transaction sequence must not exceed event sequence" }
        require(roundNumber > 0) { "History round number must be positive" }
    }
}
