package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/**
 * 以 cold Flow 交付完整交易；收集端確認後才可推進下一步。
 *
 * 不預先緩衝、不丟棄事件。收集端須在持久化寫入確認後，呼叫來源 store 的
 * acknowledgeHistoryEvents；只把資料加入正式 outbox 不能作為確認依據。
 *
 * @property runtime 真實的隔離執行環境。
 * @property limits 單場步數、批次及時間限制。
 */
class HeadlessHistoryMatchRunner(
    private val runtime: HeadlessHistoryMatchRuntime,
    private val limits: HeadlessHistoryRunLimits = HeadlessHistoryRunLimits(),
) {
    /**
     * 開始收集後才逐步執行來源對局；同一 runtime 只供單次完整收集。
     *
     * @return 完整交易進度，以及最後唯一一筆空事件終點。
     * @throws IllegalStateException 當交易過大、來源有缺口、未確認或無法收斂時。
     */
    fun events(): Flow<HeadlessHistoryProgress> = flow {
        withTimeout(limits.maxDuration) {
            val matchId = runtime.matchId()
            check(runtime.currentGame()?.matchId == matchId) { "History runtime must have an opening game" }
            var steps = 0
            while (true) {
                val snapshot = runtime.store.snapshot()
                val recording = snapshot.historyRecordingState
                check(matchId !in recording.firstMissingSequenceByMatchId) { "History source has an event sequence gap" }
                val pending = recording.pendingEvents.filter { it.matchId == matchId }
                if (pending.isNotEmpty()) {
                    val firstTransaction = pending.first().transactionFirstSequence
                    val batch = pending.takeWhile { it.transactionFirstSequence == firstTransaction }
                    check(batch.size <= limits.maxTransferBatchSize) { "A history transaction exceeds the transfer batch limit" }
                    check(batch.first().sequence == firstTransaction) { "History source transaction is incomplete" }
                    withTimeout(limits.transferWaitTimeout) {
                        emit(HeadlessHistoryProgress(snapshot, runtime.currentGame(), matchId, runtime.venueId, false, batch))
                    }
                    val remaining = runtime.store.snapshot().historyRecordingState.pendingEvents
                    val ids = batch.mapTo(mutableSetOf()) { it.matchId to it.sequence }
                    check(remaining.none { (it.matchId to it.sequence) in ids }) {
                        "History collector must acknowledge the entire emitted batch before the next step"
                    }
                    continue
                }
                if (runtime.currentGame() == null) {
                    check(recording.terminalByMatchId[matchId]?.completed == true) { "History source has no completed terminal evidence" }
                    withTimeout(limits.transferWaitTimeout) {
                        emit(HeadlessHistoryProgress(snapshot, null, matchId, runtime.venueId, true, emptyList()))
                    }
                    break
                }
                check(steps < limits.maxSteps) { "Headless history runner exceeded its step limit" }
                check(runtime.step()) { "Headless history runner made no progress before match completion" }
                steps++
            }
        }
    }
}
