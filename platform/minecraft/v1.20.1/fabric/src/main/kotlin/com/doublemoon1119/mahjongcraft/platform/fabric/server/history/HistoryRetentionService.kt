package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock

/**
 * 一次維護的實際結果，與 preview 的邏輯刪除量分離。
 *
 * @property removedMatches 實際已提交刪除的場數。
 * @property diskBefore 維護前的三檔實際用量。
 * @property diskAfter 維護後的三檔實際用量。
 * @property storageAvailable 是否仍可接受新的歷史資料。
 * @property recoveryBusy 是否有 checkpoint 因連線占用而延後。
 * @property incrementalSupported 資料庫目前是否支援有界漸進回收。
 */
internal data class HistoryCleanupResult(
    val removedMatches: Int,
    val diskBefore: HistoryDiskUsage,
    val diskAfter: HistoryDiskUsage,
    val storageAvailable: Boolean,
    val recoveryBusy: Boolean,
    val incrementalSupported: Boolean,
)

/**
 * 在 writer 的 I/O 邊界評估、整場清理與回收空間，不修改遊戲狀態。
 *
 * @property store 提供保護名單及歷史 metadata 精確確認的權威邊界。
 * @property clock 清理及到期評估使用的 UTC 時鐘。
 */
internal class HistoryRetentionService(
    private val store: AuthoritativeStateStore,
    private val clock: Clock = Clock.System,
) {
    /**
     * 取得唯讀 preview；不執行 checkpoint 或資料刪除。
     *
     * @param database 已驗證的同一 session 資料庫。
     * @param policy 有效政策快照。
     * @return 以當前保護名單評估的清理計畫。
     */
    suspend fun preview(database: SqliteHistoryDatabase, policy: HistoryRetentionPolicy): HistoryCleanupPlan = evaluateHistoryCleanup(
        database.readRetentionCandidates(),
        protectedMatchIds(),
        policy,
        clock.now(),
    )

    /**
     * 同一政策 lease 下重新評估及執行，不接受未重新驗證的舊 preview。
     *
     * @param database 目前 session 的資料庫。
     * @param policy coordinator 固定的有效政策。
     * @param onCommitted 每次 SQL 刪除已提交後的場數通知；不得阻塞或重入儲存端。
     * @return 實際刪除與磁碟回收結果。
     */
    suspend fun run(database: SqliteHistoryDatabase, policy: HistoryRetentionPolicy, onCommitted: (Int) -> Unit = {}): HistoryCleanupResult {
        val before = database.measureDiskUsage()
        val plan = preview(database, policy)
        var removed = prune(database, plan.removals, onCommitted)
        var recovery = database.recoverDiskSpace()
        var after = database.measureDiskUsage()
        for (candidate in plan.remaining) {
            currentCoroutineContext().ensureActive()
            if (after.totalBytes <= policy.maxDiskBytes || recovery.busy) break
            removed += prune(database, mapOf(candidate.matchId to HistoryCleanupReason.DISK_LIMIT), onCommitted)
            recovery = database.recoverDiskSpace()
            after = database.measureDiskUsage()
        }
        val available = after.totalBytes <= policy.maxDiskBytes
        store.applyHistoryStorageAvailability(available)
        return HistoryCleanupResult(removed, before, after, available, recovery.busy, recovery.incrementalSupported)
    }

    /**
     * 刪除前再取得保護名單；SQL 提交後才清理可移除的權威 metadata。
     *
     * @param database 同一維護工作的資料庫。
     * @param removals 此批 ID 與清理原因。
     * @param onCommitted SQL 刪除提交後立即回報場數，即使之後對帳或回收失敗也保留事實。
     * @return SQL 實際移除的場數。
     */
    private suspend fun prune(database: SqliteHistoryDatabase, removals: Map<String, HistoryCleanupReason>, onCommitted: (Int) -> Unit): Int {
        val protected = protectedMatchIds()
        val allowed = removals.filterKeys { it !in protected }
        if (allowed.isEmpty()) return 0
        currentCoroutineContext().ensureActive()
        val removed = database.pruneMatches(allowed.mapValues { it.value.name }, clock.now().toEpochMilliseconds())
        onCommitted(removed)
        val committed = database.readPruningConfirmations().filter { it.matchId.toString() in allowed }
        store.acknowledgePrunedHistory(committed)
        return removed
    }

    /** 可接續對局與尚待寫事件的聯集；終止證據不會覆蓋此保護。 */
    private suspend fun protectedMatchIds(): Set<String> {
        val snapshot = store.snapshot()
        return snapshot.games.values.mapTo(mutableSetOf()) { it.matchId.toString() } +
            snapshot.historyRecordingState.transfersByMatchId.keys.map { it.toString() } +
            snapshot.historyRecordingState.pendingEvents.map { it.matchId.toString() }
    }
}
