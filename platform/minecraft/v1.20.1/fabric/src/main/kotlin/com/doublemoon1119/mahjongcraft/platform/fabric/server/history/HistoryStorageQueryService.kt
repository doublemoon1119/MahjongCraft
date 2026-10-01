package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import kotlin.time.Clock

/**
 * 組合 SQL 統計、實際檔案大小與權威記錄證據，不另建資料庫生命週期。
 *
 * @property store 權威活動場次與待寫事件來源。
 * @property clock 快照完成時間的 UTC 時鐘。
 */
internal class HistoryStorageQueryService(
    private val store: AuthoritativeStateStore,
    private val clock: Clock = Clock.System,
) {
    /**
     * 在呼叫端的 I/O 與政策 lease 下取得一份用量快照。
     *
     * @param database 同一 session 的已驗證資料庫。
     * @param policy 此次固定的保留政策。
     * @return 完整快照；讀取失敗時拋出例外，不假造零用量。
     */
    suspend fun read(database: SqliteHistoryDatabase, policy: HistoryRetentionPolicy): HistoryStorageSnapshot {
        val statistics = database.readStatistics()
        val authoritative = store.snapshot()
        val evidence = recordingEvidence(authoritative) + statistics.matchStates.keys
        val active = (
            authoritative.games.values.mapTo(mutableSetOf()) { it.matchId.toString() } +
                authoritative.historyRecordingState.transfersByMatchId.keys.map { it.toString() }
            ).intersect(evidence)
        val states = evidence.associateWith { statistics.matchStates[it] ?: HistoryStoredMatchState.UNKNOWN }
        return HistoryStorageSnapshot(
            completedMatchCount = states.count { (id, state) -> id !in active && state == HistoryStoredMatchState.COMPLETED }.toLong(),
            activeMatchCount = active.size.toLong(),
            partialMatchCount = states.count { (id, state) -> id !in active && state == HistoryStoredMatchState.PARTIAL }.toLong(),
            unknownMatchCount = states.count { (id, state) -> id !in active && state == HistoryStoredMatchState.UNKNOWN }.toLong(),
            pendingSqlEventCount = statistics.pendingEventCount,
            pendingOutboxEventCount = authoritative.historyRecordingState.pendingEvents.size.toLong(),
            tombstoneCount = statistics.tombstoneCount,
            disk = database.measureDiskUsage(),
            policy = policy,
            updatedAt = clock.now(),
        )
    }

    /**
     * 取得固定資格、序號或待寫資料證明曾開始記錄的場次。
     *
     * @param snapshot 同一工作取得的權威快照，不阻塞所有玩家交易。
     * @return 不含明確排除資格的記錄證據 ID。
     */
    private fun recordingEvidence(snapshot: AuthoritativeStateSnapshot): Set<String> {
        val recording = snapshot.historyRecordingState
        val decisions = recording.decisionsByMatchId.filterValues {
            it == HistoryRecordingDecision.RECORDING ||
                it == HistoryRecordingDecision.STOPPED_CONFIG_DISABLED ||
                it == HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE ||
                it == HistoryRecordingDecision.STOPPED_PRUNED ||
                it == HistoryRecordingDecision.STOPPED_TRANSFER_INTERRUPTED
        }.keys
        return (
            recording.nextSequenceByMatchId.keys + recording.firstMissingSequenceByMatchId.keys +
                recording.terminalByMatchId.keys + recording.pendingEvents.map { it.matchId } + decisions
            ).mapTo(mutableSetOf()) { it.toString() }
    }
}
