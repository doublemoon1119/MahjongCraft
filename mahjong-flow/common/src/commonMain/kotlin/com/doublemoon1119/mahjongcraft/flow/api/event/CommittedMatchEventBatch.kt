package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 同一筆權威提交產生的事件批次。
 *
 * 這個型別不是 [MatchEvent]；它用來保留同一筆提交的邊界，讓訂閱者不會把一筆交易拆成多次處理。
 *
 * @property batchId 此次提交批次的唯一識別碼。
 * @property matchId 場次 UUID。
 * @property venueId 場地 UUID。
 * @property ruleModuleId 對局規則模組的 namespaced ID。
 * @property players 這個批次所屬場次的玩家快照。
 * @property events 依發生順序排列的事件。
 */
class CommittedMatchEventBatch private constructor(
    val batchId: Uuid,
    val matchId: Uuid,
    val venueId: Uuid,
    val ruleModuleId: String,
    val players: List<MatchPlayer>,
    val events: List<MatchEvent>,
) {
    /** 事件系統內部建立批次的入口。 */
    internal companion object {
        /**
         * 建立事件批次，並複製玩家與事件清單。
         *
         * @param batchId 此次提交批次的唯一識別碼。
         * @param matchId 場次 UUID。
         * @param venueId 場地 UUID。
         * @param ruleModuleId 對局規則模組的 namespaced ID。
         * @param players 場次玩家快照。
         * @param events 依發生順序排列的事件。
         * @return 建立的事件批次。
         */
        @JvmSynthetic
        fun create(
            batchId: Uuid,
            matchId: Uuid,
            venueId: Uuid,
            ruleModuleId: String,
            players: List<MatchPlayer>,
            events: List<MatchEvent>,
        ): CommittedMatchEventBatch = CommittedMatchEventBatch(
            batchId = batchId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = ruleModuleId,
            players = ReadOnlyListSnapshot(players),
            events = ReadOnlyListSnapshot(events),
        )
    }
}
