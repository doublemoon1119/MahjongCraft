package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** 保留服務的 SQL、權威保護名單與政策發布整合測試。 */
class HistoryRetentionServiceTest {
    /** 新完成資料先保存，再依場數移除最舊紀錄；preview 不寫入 tombstone。 */
    @Test
    fun `test preview is read only and run retains newest completed match`() = runBlocking {
        val database = database()
        val old = archive(Uuid.random(), 100L)
        val newest = archive(Uuid.random(), 200L)
        database.archive(old)
        database.archive(newest)
        val store = AuthoritativeStateStore()
        val service = HistoryRetentionService(store, fixedClock())
        val preview = service.preview(database, policy())
        assertEquals(setOf(old.matchId), preview.removals.keys)
        assertTrue(database.readTombstones().isEmpty())
        val result = service.run(database, policy())
        assertEquals(1, result.removedMatches)
        assertEquals(setOf(newest.matchId), database.readReplayIds())
        assertEquals(setOf(old.matchId), database.readTombstones())
        assertTrue(result.storageAvailable)
    }

    /** 回復的可接續 Game 優先於資料庫摘要，不得因場數限制刪除。 */
    @Test
    fun `test active match is protected even when archive exists`() = runBlocking {
        val game = Game(FakeTableStateFactory.create(), GameFlowConfig())
        val database = database()
        database.archive(archive(game.matchId, 100L))
        val other = archive(Uuid.random(), 200L)
        database.archive(other)
        val store = AuthoritativeStateStore()
        store.load(AuthoritativeStateSnapshot(games = mapOf(game.id to game)))
        val result = HistoryRetentionService(store, fixedClock()).run(database, policy())
        assertEquals(0, result.removedMatches)
        assertEquals(setOf(game.matchId.toString(), other.matchId), database.readReplayIds())
        assertEquals(game, store.getGame(game.id))
    }

    /** 只有受保護資料時容量暫停不刪除 Game；提高限制後解除暫停。 */
    @Test
    fun `test protected data pauses recording and recovers after limit increase`() = runBlocking {
        val game = Game(FakeTableStateFactory.create(), GameFlowConfig())
        val database = database()
        database.archive(archive(game.matchId, 100L))
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        store.load(
            AuthoritativeStateSnapshot(
                games = mapOf(game.id to game),
                historyRecordingState = HistoryRecordingState(nextSequenceByMatchId = mapOf(game.matchId to 2L)),
            ),
        )
        val service = HistoryRetentionService(store, fixedClock())
        val stopped = service.run(database, policy().copy(maxDiskBytes = 1))
        assertFalse(stopped.storageAvailable)
        assertFalse(store.isHistoryStorageAvailable)
        assertTrue(store.isHistoryRecordingEnabled)
        assertEquals(game, store.getGame(game.id))
        assertTrue(database.readTombstones().isEmpty())
        assertTrue(service.run(database, policy()).storageAvailable)
        assertTrue(store.isHistoryStorageAvailable)
    }

    /** 政策 reload 等待正在執行的 lease，不讓舊政策越過發布完成邊界。 */
    @Test
    fun `test policy update waits for active cleanup lease`() = runBlocking {
        val coordinator = HistoryRetentionCoordinator()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val current = policy()
        coordinator.apply(current)
        val maintenance = async {
            coordinator.withPolicy { snapshot ->
                assertEquals(current, snapshot)
                started.complete(Unit)
                finish.await()
            }
        }
        started.await()
        val next = current.copy(maxMatches = 2)
        val reload = async { coordinator.apply(next) }
        assertFalse(reload.isCompleted)
        finish.complete(Unit)
        maintenance.await()
        reload.await()
        coordinator.withPolicy { assertEquals(next, it) }
    }

    /** 建立只存於暫存目錄的受測資料庫。 */
    private fun database(): SqliteHistoryDatabase = SqliteHistoryDatabase.open(createTempDirectory("history-maintenance-").resolve("history.sqlite"))

    /** 固定評估時刻，避免到期測試受真實時間影響。 */
    private fun fixedClock(): Clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_000L)
    }

    /** 保留一場且不按天數清理的安全預設測試政策。 */
    private fun policy(): HistoryRetentionPolicy = HistoryRetentionPolicy(false, 1, Duration.ZERO, Long.MAX_VALUE)

    /**
     * 建立已驗證封存邊界的最小 SQL 測試資料，不作為正式對局生成器。
     *
     * @param id 對局 ID。
     * @param ended 結束 UTC 時間。
     * @return 受測封存紀錄。
     */
    private fun archive(id: Uuid, ended: Long): HistoryArchiveRecord = HistoryArchiveRecord(
        id.toString(), Uuid.random().toString(), "test:rule", null, 0L, ended,
        emptyList(), emptyList(), "{}",
    )
}
