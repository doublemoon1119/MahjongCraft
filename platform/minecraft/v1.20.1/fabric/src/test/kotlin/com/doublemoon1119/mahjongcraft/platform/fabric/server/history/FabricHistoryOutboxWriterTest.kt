package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.sql.DriverManager
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
    /** 保留部分紀錄時，SQL 終止證據與缺口提交後可清除已排空的權威 metadata。 */
    @Test
    fun `retained partial metadata is acknowledged only after durable synchronization`() = runBlocking {
        val pending = event()
        val store = AuthoritativeStateStore()
        store.load(
            AuthoritativeStateSnapshot(
                historyRecordingState = HistoryRecordingState(
                    pendingEvents = listOf(pending),
                    nextSequenceByMatchId = mapOf(pending.matchId to 3L),
                    firstMissingSequenceByMatchId = mapOf(pending.matchId to 2L),
                    terminalByMatchId = mapOf(pending.matchId to HistoryRecordingTerminal(100L, false, pending.venueId)),
                ),
            ),
        )
        val path = createTempDirectory("history-retained-partial-").resolve("history.sqlite")
        val config = MinecraftServerConfigState(MinecraftServerConfig(history = MinecraftHistoryConfig(includeInterruptedMatches = true, retentionDays = 0)))
        val writer = writer(store, config)
        try {
            writer.attach(path)
            withTimeout(5.seconds) {
                while (store.snapshot().historyRecordingState.terminalByMatchId.isNotEmpty()) delay(10.milliseconds)
            }
            val recording = store.snapshot().historyRecordingState
            assertTrue(recording.pendingEvents.isEmpty())
            assertTrue(recording.nextSequenceByMatchId.isEmpty())
            val database = SqliteHistoryDatabase.open(path)
            assertEquals(setOf(pending.matchId.toString()), database.readTerminalPartialIds())
            assertEquals(2L, database.readGaps()[pending.matchId.toString()])
            assertEquals(1, database.readPending(pending.matchId.toString()).size)
            assertTrue(database.readReplayIds().isEmpty())
        } finally {
            writer.detach()
        }
    }

    /** 已清理場次的舊 outbox 重啟後只丟棄已證明的場次，不製造新缺口。 */
    @Test
    fun `tombstoned inactive outbox is acknowledged without resurrection`() = runBlocking {
        val pending = event()
        val store = AuthoritativeStateStore()
        store.load(AuthoritativeStateSnapshot(historyRecordingState = HistoryRecordingState(pendingEvents = listOf(pending))))
        val path = createTempDirectory("history-tombstone-restart-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        database.recordTerminals(listOf(HistoryTerminalRecord(pending.matchId.toString(), pending.venueId.toString(), 100L, false)))
        database.pruneMatches(mapOf(pending.matchId.toString() to "EXPIRED"), 101L)
        val writer = writer(store)
        try {
            writer.attach(path)
            assertTrue(store.snapshot().historyRecordingState.pendingEvents.isEmpty())
            assertTrue(database.readPending(pending.matchId.toString()).isEmpty())
            assertEquals(0, writer.status().knownGapCount)
        } finally {
            writer.detach()
        }
    }

    /** 舊存檔中的可接續場次即使有 tombstone 也保留 Game 與原有待寫事件。 */
    @Test
    fun `tombstoned active outbox remains protected and stops appending`() = runBlocking {
        val game = Game(FakeTableStateFactory.create(), GameFlowConfig())
        val pending = event().copy(matchId = game.matchId, venueId = game.id)
        val store = AuthoritativeStateStore()
        store.load(
            AuthoritativeStateSnapshot(
                games = mapOf(game.id to game),
                historyRecordingState = HistoryRecordingState(pendingEvents = listOf(pending)),
            ),
        )
        val path = createTempDirectory("history-active-tombstone-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        database.recordTerminals(listOf(HistoryTerminalRecord(game.matchId.toString(), game.id.toString(), 100L, false)))
        database.pruneMatches(mapOf(game.matchId.toString() to "EXPIRED"), 101L)
        val writer = writer(store)
        try {
            writer.attach(path)
            delay(50.milliseconds)
            assertEquals(game, store.getGame(game.id))
            assertEquals(listOf(pending), store.snapshot().historyRecordingState.pendingEvents)
            assertEquals(HistoryRecordingDecision.STOPPED_PRUNED, store.snapshot().historyRecordingState.decisionsByMatchId[game.matchId])
            assertTrue(database.readPending(game.matchId.toString()).isEmpty())
        } finally {
            writer.detach()
        }
    }

    /** 容量不足與設定總開關分開回報，未知紀錄超標時不刪除資料。 */
    @Test
    fun `oversized unknown data pauses storage without disabling policy`() = runBlocking {
        val path = createTempDirectory("history-capacity-pause-").resolve("history.sqlite")
        val database = SqliteHistoryDatabase.open(path)
        val id = Uuid.random().toString()
        database.appendPending(PendingHistoryRecord(id, 1L, 1, 100L, 1, "x".repeat(2 * 1024 * 1024)))
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val writer = writer(store, MinecraftServerConfigState(MinecraftServerConfig(history = MinecraftHistoryConfig(maxDiskMiB = 1L))))
        try {
            writer.attach(path)
            val status = writer.status()
            assertTrue(status.databaseConnected)
            assertTrue(status.recordingEnabled)
            assertTrue(status.storagePaused)
            assertTrue(database.readTombstones().isEmpty())
            assertEquals(1, database.readPending(id).size)
        } finally {
            writer.detach()
        }
    }

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

    /** 資料庫開啟失敗時，原本啟用的有效記錄政策仍須保留。 */
    @Test
    fun `failed open preserves enabled recording policy`() = runBlocking {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val writer = writer(store)
        val invalidPath = createTempDirectory("mahjongcraft-history-writer-")

        writer.attach(invalidPath)

        assertTrue(store.isHistoryRecordingEnabled)
        writer.detach()
    }

    /** 成功建立資料庫連線不得將停用的有效記錄政策改為啟用。 */
    @Test
    fun `successful open does not enable disabled recording policy`() = runBlocking {
        val store = AuthoritativeStateStore()
        val writer = writer(store)
        val path = createTempDirectory("mahjongcraft-history-writer-").resolve("history.sqlite")

        writer.attach(path)

        assertFalse(store.isHistoryRecordingEnabled)
        writer.detach()
    }

    /** 斷開 writer 後，原本啟用的有效記錄政策仍須保留。 */
    @Test
    fun `enabled recording policy remains after detach`() = runBlocking {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val writer = writer(store)
        val path = createTempDirectory("mahjongcraft-history-writer-").resolve("history.sqlite")

        writer.attach(path)
        writer.detach()

        assertTrue(store.isHistoryRecordingEnabled)
    }

    /** 沒有待寫事件時，設定停用的場次診斷仍須提交至資料庫。 */
    @Test
    fun `stopped recording decision is persisted with an empty outbox`() = runBlocking {
        val matchId = Uuid.random()
        val store = AuthoritativeStateStore()
        store.load(
            AuthoritativeStateSnapshot(
                historyRecordingState = HistoryRecordingState(
                    decisionsByMatchId = mapOf(matchId to HistoryRecordingDecision.STOPPED_CONFIG_DISABLED),
                ),
            ),
        )
        val writer = writer(store)
        val path = createTempDirectory("mahjongcraft-history-writer-").resolve("history.sqlite")

        writer.attach(path)
        writer.detach()

        assertEquals(
            mapOf(matchId.toString() to "PARTIAL_CONFIG_DISABLED"),
            SqliteHistoryDatabase.open(path).readRecordingStops(),
        )
    }

    /** 只有場次已離開權威遊戲且沒有待寫事件時，停止診斷才可由 decisions 移除。 */
    @Test
    fun `decision acknowledgement retains active games and pending events`() = runBlocking {
        val activeGame = Game(FakeTableStateFactory.create(), GameFlowConfig())
        val pending = event()
        val removedMatchId = Uuid.random()
        val store = AuthoritativeStateStore()
        store.load(
            AuthoritativeStateSnapshot(
                games = mapOf(activeGame.id to activeGame),
                historyRecordingState = HistoryRecordingState(
                    pendingEvents = listOf(pending),
                    decisionsByMatchId = mapOf(
                        activeGame.matchId to HistoryRecordingDecision.STOPPED_CONFIG_DISABLED,
                        pending.matchId to HistoryRecordingDecision.STOPPED_CONFIG_DISABLED,
                        removedMatchId to HistoryRecordingDecision.STOPPED_CONFIG_DISABLED,
                    ),
                ),
            ),
        )
        val allMatchIds = setOf(activeGame.matchId, pending.matchId, removedMatchId)
        store.acknowledgeHistoryDecisions(allMatchIds)
        assertEquals(
            setOf(activeGame.matchId, pending.matchId),
            store.snapshot().historyRecordingState.decisionsByMatchId.keys,
        )

        store.acknowledgeHistoryEvents(setOf(pending.matchId to pending.sequence))
        store.acknowledgeHistoryDecisions(setOf(pending.matchId))
        assertEquals(
            setOf(activeGame.matchId),
            store.snapshot().historyRecordingState.decisionsByMatchId.keys,
        )
    }

    /** 資料庫缺少必要的表而無法開啟時暫停記錄，新對局只留缺口，不累積待寫事件。 */
    @Test
    fun `failed open pauses recording for new games`() = runBlocking {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val writer = writer(store)
        writer.attach(databaseMissingResultTables())
        val game = startMatch(store)

        val recording = store.snapshot().historyRecordingState
        assertTrue(store.isHistoryRecordingEnabled, "Connection failure must not change policy")
        assertFalse(store.isHistoryStorageAvailable)
        assertTrue(writer.status().storagePaused)
        assertTrue(recording.pendingEvents.isEmpty())
        assertEquals(1L, recording.firstMissingSequenceByMatchId[game.matchId])
        assertEquals(HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE, recording.decisionsByMatchId[game.matchId])
        writer.detach()
    }

    /** 開啟失敗時保留的待寫事件，在之後成功開啟的 session 寫入資料庫並恢復記錄。 */
    @Test
    fun `pending events from failed open are written once a later session opens`() = runBlocking {
        val pending = event()
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        store.load(AuthoritativeStateSnapshot(historyRecordingState = HistoryRecordingState(pendingEvents = listOf(pending))))
        val writer = writer(store)
        writer.attach(databaseMissingResultTables())
        assertEquals(listOf(pending), store.snapshot().historyRecordingState.pendingEvents)
        writer.detach()

        val path = createTempDirectory("history-reopen-").resolve("history.sqlite")
        writer.attach(path)
        try {
            withTimeout(5.seconds) {
                while (store.snapshot().historyRecordingState.pendingEvents.isNotEmpty()) delay(10.milliseconds)
            }
            assertTrue(store.isHistoryStorageAvailable)
            assertEquals(1, SqliteHistoryDatabase.open(path).readPending(pending.matchId.toString()).size)
        } finally {
            writer.detach()
        }
    }

    /** 背景寫入遇到 SQL 錯誤而停止時暫停記錄，之後的對局不再累積待寫事件。 */
    @Test
    fun `SQL failure during writing pauses recording`() = runBlocking {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val writer = writer(store)
        val path = createTempDirectory("history-sql-failure-").resolve("history.sqlite")
        writer.attach(path)
        try {
            assertTrue(writer.status().databaseConnected)
            dropTable(path, "history_pending_event")
            startMatch(store)
            withTimeout(5.seconds) {
                while (writer.status().databaseConnected) delay(10.milliseconds)
            }

            assertFalse(store.isHistoryStorageAvailable)
            val later = startMatch(store)
            val recording = store.snapshot().historyRecordingState
            assertTrue(recording.pendingEvents.none { it.matchId == later.matchId })
            assertEquals(HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE, recording.decisionsByMatchId[later.matchId])
        } finally {
            writer.detach()
        }
    }

    /**
     * 建立缺少結果相關表的歷史資料庫，模擬較舊格式建立的資料庫。
     *
     * @return 無法通過結構驗證的資料庫檔案位置。
     */
    private fun databaseMissingResultTables(): Path {
        val path = createTempDirectory("history-missing-tables-").resolve("history.sqlite")
        SqliteHistoryDatabase.open(path)
        dropTable(path, "history_participant_result")
        dropTable(path, "history_result_projection")
        return path
    }

    /**
     * 以獨立連線直接刪除資料庫中的一張表。
     *
     * @param path 歷史資料庫檔案位置。
     * @param table 要刪除的表名稱。
     */
    private fun dropTable(path: Path, table: String) {
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { statement -> statement.execute("DROP TABLE $table") }
        }
    }

    /**
     * 透過正式 repository 開始一場會產生歷史事件的新對局。
     *
     * @param store 受測的權威歷史來源。
     * @return 已提交的對局。
     */
    private suspend fun startMatch(store: AuthoritativeStateStore): Game {
        val table = FakeTableStateFactory.create()
        GameRepositoryImpl(store).updateGame(table.id, history = { _, _, _ ->
            listOf(HistoryEventDraft(null, HistoryFact.MatchStarted(table, GameFlowConfig(), emptyMap())))
        }) { Game(table, GameFlowConfig()) to Unit }
        return checkNotNull(store.getGame(table.id))
    }

    /**
     * 建立與 production 相同的 mapper、JSON 與 I/O dispatcher 邊界。
     *
     * @param store 受測的權威歷史來源。
     * @param configState 此 session 的有效設定。
     * @return 未附加資料庫的 writer。
     */
    private fun writer(store: AuthoritativeStateStore, configState: MinecraftServerConfigState = MinecraftServerConfigState()): FabricHistoryOutboxWriter = FabricHistoryOutboxWriter(
        store = store,
        registries = buildBuiltInPersistenceRegistries(),
        replayProjectionRegistry = buildTestHistoryReplayProjectionRegistry(),
        json = Json,
        moduleRegistry = MahjongModuleRegistryImpl(),
        locations = TableLocationRegistry(),
        configState = configState,
        retentionCoordinator = HistoryRetentionCoordinator(),
        dispatchers = object : CoroutineDispatchers {
            override val default: CoroutineDispatcher = Dispatchers.Default
            override val io: CoroutineDispatcher = Dispatchers.IO
            override val main: CoroutineDispatcher = Dispatchers.Default
        },
    )

    /** 不依賴桌況快照的最小可序列化事件。 */
    private fun event(): HistoryOutboxEvent = HistoryOutboxEvent(
        matchId = Uuid.random(),
        venueId = Uuid.random(),
        roundNumber = 1,
        sequence = 1L,
        occurredAtEpochMillis = 100L,
        actorPlayerId = null,
        fact = HistoryFact.ReturnedToRoom,
    )
}
