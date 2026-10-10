package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 一筆已指派穩定鍵、可重試寫入的歷史事件。
 *
 * @property matchId 事件所屬對局的 UUID 字串。
 * @property venueId 事件所屬場地的 UUID 字串。
 * @property roundNumber 事件發生時所在的局數。
 * @property sequence 在同一對局內單調遞增的事件序號。
 * @property occurredAtEpochMillis 事件發生時間的 Unix epoch 毫秒值。
 * @property actorPlayerId 觸發事件的玩家 UUID 字串；系統事件為 null。
 * @property fact 事件的具體歷史事實。
 * @property transactionFirstSequence 同一權威交易第一筆事件的序號。
 */
@Serializable
data class HistoryOutboxEventPersistenceDto(
    val matchId: String,
    val venueId: String,
    val roundNumber: Int,
    val sequence: Long,
    val occurredAtEpochMillis: Long,
    val actorPlayerId: String?,
    val fact: HistoryFactPersistenceDto,
    val transactionFirstSequence: Long,
)
