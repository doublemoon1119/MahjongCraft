package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** 驗證背景 writer 在 SQLite 提交後才確認權威 outbox。 */
class FabricHistoryOutboxWriterTest {
    /** 已保存的待寫事件能跨資料庫重新開啟讀回，且確認不清除缺口。 */
    @Test
    fun `committed events are acknowledged without clearing gaps`() = runBlocking {
        val store = AuthoritativeStateStore()
        val event = event()
        store.load(
            AuthoritativeStateSnapshot(
                historyRecordingState = HistoryRecordingState(
                    nextSequenceByMatchId = mapOf(event.matchId to 3L),
                    pendingEvents = listOf(event),
                    firstMissingSequenceByMatchId = mapOf(event.matchId to 2L),
                ),
            ),
        )
        val writer = writer(store)
        val path = createTempDirectory("mahjongcraft-history-writer-").resolve("history.sqlite")

        writer.attach(path)
        withTimeout(5.seconds) {
            while (store.snapshot().historyRecordingState.pendingEvents.isNotEmpty()) delay(10.milliseconds)
        }
        writer.detach()

        assertEquals(1, SqliteHistoryDatabase.open(path).readPending(event.matchId.toString()).size)
        assertEquals(3L, store.snapshot().historyRecordingState.nextSequenceByMatchId[event.matchId])
        assertEquals(2L, store.snapshot().historyRecordingState.firstMissingSequenceByMatchId[event.matchId])
        assertEquals(2L, SqliteHistoryDatabase.open(path).readGaps()[event.matchId.toString()])
        assertFalse(store.isHistoryRecordingEnabled)
    }

    /** 已寫入的同鍵異內容不可被重送覆寫，待寫事件與原始資料均保留。 */
    @Test
    fun `conflicting startup event remains pending and blocks its match`() = runBlocking {
        val event = event()
        val store = AuthoritativeStateStore()
        store.load(AuthoritativeStateSnapshot(historyRecordingState = HistoryRecordingState(pendingEvents = listOf(event))))
        val path = createTempDirectory("mahjongcraft-history-writer-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        database.appendPending(PendingHistoryRecord(event.matchId.toString(), 1, 1, 100, 1, "different"))

        val writer = writer(store)
        writer.attach(path)

        assertEquals(listOf(event), store.snapshot().historyRecordingState.pendingEvents)
        assertEquals("different", database.readPending(event.matchId.toString()).single().payload)
        assertTrue(writer.status().knownGapCount > 0)
        writer.detach()
    }

    /** 資料庫無法開啟時不啟用記錄，也不移除既有待寫事件。 */
    @Test
    fun `failed open keeps recording disabled and pending event intact`() = runBlocking {
        val store = AuthoritativeStateStore()
        val event = event()
        store.load(
            AuthoritativeStateSnapshot(
                historyRecordingState = HistoryRecordingState(pendingEvents = listOf(event)),
            ),
        )
        val writer = writer(store)
        val invalidPath = createTempDirectory("mahjongcraft-history-writer-")

        writer.attach(invalidPath)

        assertFalse(store.isHistoryRecordingEnabled)
        assertEquals(listOf(event), store.snapshot().historyRecordingState.pendingEvents)
        writer.detach()
    }

    /** 建立與 production 相同的 mapper、JSON 與 I/O dispatcher 邊界。 */
    private fun writer(store: AuthoritativeStateStore): FabricHistoryOutboxWriter = FabricHistoryOutboxWriter(
        store = store,
        registries = buildBuiltInPersistenceRegistries(),
        json = Json,
        moduleRegistry = MahjongModuleRegistryImpl(),
        locations = TableLocationRegistry(),
        dispatchers = object : CoroutineDispatchers {
            override val default: CoroutineDispatcher = Dispatchers.Default
            override val io: CoroutineDispatcher = Dispatchers.IO
            override val main: CoroutineDispatcher = Dispatchers.Default
        },
    )

    /** 不依賴桌況快照的最小可序列化事件。 */
    private fun event(): HistoryOutboxEvent = HistoryOutboxEvent(
        matchId = Uuid.random(),
        tableId = Uuid.random(),
        roundNumber = 1,
        sequence = 1L,
        occurredAtEpochMillis = 100L,
        actorPlayerId = null,
        fact = HistoryFact.ReturnedToRoom,
    )
}
