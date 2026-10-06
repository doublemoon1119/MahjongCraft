package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTransferResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryListRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryAccess
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.query.HistoryQueryScope
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessFlowHistoryRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryMatchRunner
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryProgress
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryRunLimits
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * 驗證歷史生成工具使用真實 Flow、正式 writer 與 SQLite 邊界。
 *
 * 這些測試刻意不使用平台玩家集合或測試用對局 fixture，確保生成場次不會污染線上遊戲與房間狀態。
 */
class HistoryGenerationIntegrationTest {
    /** 測試用 logger；壓力測試只輸出彙總，不逐事件污染測試 log。 */
    private val logger = mahjongCraftLogger(HistoryGenerationIntegrationTest::class)

    /** 驗證東風戰完整生成超過有界佇列容量後，SQLite replay 仍連續且沒有序號缺口。 */
    @Test
    fun `real east generation archives more than bounded queue without gaps`() = runBlocking {
        val runtime = runtime()
        val liveStore = liveStore()
        val path = createTempDirectory("mahjongcraft-history-generation-").resolve("history.sqlite")
        val writer = writer(liveStore)
        var acknowledged = 0
        try {
            writer.attach(path)
            val session = checkNotNull(writer.currentSessionId)
            val database = SqliteHistoryDatabase.open(path)
            acknowledged = generate(runtime, writer, liveStore, database, session)

            val matchId = runtime.matchId().toString()
            assertTrue(acknowledged > 256, "The real match should produce more than the bounded queue capacity.")
            assertTrue(database.readReplayIds().contains(matchId), "The completed match must have a replay.")
            assertTrue(database.readPending(matchId).isEmpty(), "The writer must drain the source outbox.")
            assertTrue(matchId !in database.readGaps(), "The completed match must not have a sequence gap.")
            assertTrue(liveStore.snapshot().games.isEmpty(), "History generation must not add a live game.")
            assertTrue(liveStore.snapshot().rooms.isEmpty(), "History generation must not add a live room.")
            assertEveryArchivedRoundReadable(writer, database, session, runtime.matchId())
        } finally {
            writer.detach()
        }
    }

    /**
     * 確認封存局列表中的每一局都能經由正式查詢路徑讀出事件與開局牌面。
     *
     * @param writer 已附加資料庫的正式 writer。
     * @param database writer 所附加的 SQLite 資料庫。
     * @param session writer session 識別碼。
     * @param matchId 已封存的對局。
     */
    private suspend fun assertEveryArchivedRoundReadable(
        writer: FabricHistoryOutboxWriter,
        database: SqliteHistoryDatabase,
        session: Uuid,
        matchId: Uuid,
    ) {
        val access = HistoryQueryAccess(Uuid.random(), isAdministrator = true)
        val entry = checkNotNull(
            database.readHistoryQueryPage(
                HistoryListRequest(scope = HistoryQueryScope.ALL, pageSize = 1).toSqliteQuery(access, emptySet()).copy(matchId = matchId.toString()),
            ).entries.singleOrNull(),
        )
        assertTrue(entry.rounds.size > 1, "The generated match should archive more than one round.")
        entry.rounds.forEach { round ->
            val events = writer.queryRoundEvents(access, matchId, HistoryQueryScope.ALL, round.roundNumber, 0, 1, session)
            val state = writer.queryRoundState(access, matchId, HistoryQueryScope.ALL, round.roundNumber, HistoryRoundPosition.Initial, session)
            assertIs<HistoryQueryResult.Success<*>>(assertIs<HistoryManagementResult.Success<*>>(events).value, "Round ${round.roundNumber} events must be readable.")
            assertIs<HistoryQueryResult.Success<*>>(assertIs<HistoryManagementResult.Success<*>>(state).value, "Round ${round.roundNumber} state must be readable.")
        }
    }

    /**
     * 在同一個正式 writer session 中生成並確認一場隔離對局。
     *
     * @param runtime 隔離的四 AI Flow runtime。
     * @param writer 正式 SQLite outbox writer。
     * @param liveStore writer 使用的獨立 live store。
     * @param database writer 所附加的 SQLite 資料庫。
     * @param session writer session 識別碼。
     * @return 已由 writer 寫入並由來源確認的事件數。
     */
    private suspend fun generate(
        runtime: HeadlessFlowHistoryRuntime,
        writer: FabricHistoryOutboxWriter,
        liveStore: AuthoritativeStateStore,
        database: SqliteHistoryDatabase,
        session: Uuid,
    ): Int = DriverManager.getConnection("jdbc:sqlite:${database.path.toAbsolutePath()}").use { connection ->
        var begun = false
        var acknowledged = 0
        HeadlessHistoryMatchRunner(
            runtime,
            limits = HeadlessHistoryRunLimits(maxDuration = 5.minutes, transferWaitTimeout = 30.seconds),
        ).events().collect { progress ->
            if (!begun) {
                val game = checkNotNull(progress.game)
                val result = checkNotNull(writer.beginGeneration(game, session) as? HistoryManagementResult.Success)
                check(result.value) { "The live writer store must accept the isolated generation." }
                begun = true
            }
            val ids = transfer(writer, liveStore, connection, progress, session)
            runtime.store.acknowledgeHistoryEvents(ids)
            acknowledged += ids.size
        }
        acknowledged
    }

    /** 在明確環境變數啟用時，實際寫入一百場並記錄 SQLite 主檔與 sidecar 用量。 */
    @Test
    fun `opt in one hundred match SQLite history stress`() = runBlocking {
        if (System.getenv("MAHJONGCRAFT_HISTORY_STRESS") != "true") return@runBlocking
        val liveStore = liveStore()
        val path = createTempDirectory("mahjongcraft-history-stress-").resolve("history.sqlite")
        val writer = writer(
            liveStore,
            MinecraftHistoryConfig(includeAiMatches = true, maxMatches = 100),
        )
        var events = 0
        var replayBytes = 0L
        var largestReplayBytes = 0L
        val generatedMatchIds = mutableSetOf<String>()
        val started = TimeSource.Monotonic.markNow()
        try {
            writer.attach(path)
            val session = checkNotNull(writer.currentSessionId)
            repeat(100) {
                val generatedRuntime = runtime()
                events += generate(generatedRuntime, writer, liveStore, SqliteHistoryDatabase.open(path), session)
                val receipt = checkNotNull(writer.generationReceipt(setOf(generatedRuntime.matchId()), session) as? HistoryManagementResult.Success)
                val bytes = receipt.value.replayBytes[generatedRuntime.matchId().toString()] ?: 0L
                generatedMatchIds += generatedRuntime.matchId().toString()
                replayBytes += bytes
                largestReplayBytes = maxOf(largestReplayBytes, bytes)
            }
            val usage = SqliteHistoryDatabase.open(path).measureDiskUsage()
            val archived = SqliteHistoryDatabase.open(path).readReplayIds()
            assertEquals(100, generatedMatchIds.size, "Exactly 100 matches should be generated.")
            assertTrue(generatedMatchIds.all { it in archived }, "Every generated match should be archived.")
            logger.info(
                "History stress completed: matches=100, events={}, elapsedMs={}, avgReplayBytes={}, largestReplayBytes={}, dbBytes={}, walBytes={}, shmBytes={}",
                events,
                started.elapsedNow().inWholeMilliseconds,
                replayBytes / 100,
                largestReplayBytes,
                usage.dbBytes,
                usage.walBytes,
                usage.shmBytes,
            )
        } finally {
            writer.detach()
        }
    }

    /** 驗證 AI 對局在停用 AI 記錄政策時被拒絕，且不會寫入生成資料。 */
    @Test
    fun `real writer rejects AI generation when policy excludes AI matches`() = runBlocking {
        val runtime = runtime()
        val liveStore = liveStore()
        val path = createTempDirectory("mahjongcraft-history-ai-policy-").resolve("history.sqlite")
        val writer = writer(
            liveStore,
            MinecraftHistoryConfig(includeAiMatches = false),
        )
        try {
            writer.attach(path)
            val session = checkNotNull(writer.currentSessionId)
            val result = writer.beginGeneration(checkNotNull(runtime.currentGame()), session)

            val outcome = checkNotNull(result as? HistoryManagementResult.Success)
            assertEquals(false, outcome.value)
            assertTrue(SqliteHistoryDatabase.open(path).readReplayIds().isEmpty())
        } finally {
            writer.detach()
        }
    }

    /** 驗證小容量保留政策實際清理舊 replay，且兩場生成均無序號缺口或 live 狀態污染。 */
    @Test
    fun `real SQLite retention prunes the oldest match at max matches one`() = runBlocking {
        withTimeout(60.seconds) {
            val liveStore = liveStore()
            val path = createTempDirectory("mahjongcraft-history-retention-").resolve("history.sqlite")
            val writer = writer(liveStore, MinecraftHistoryConfig(includeAiMatches = true, maxMatches = 1))
            try {
                writer.attach(path)
                val session = checkNotNull(writer.currentSessionId)
                val first = runtime()
                val second = runtime()
                generate(first, writer, liveStore, SqliteHistoryDatabase.open(path), session)
                generate(second, writer, liveStore, SqliteHistoryDatabase.open(path), session)

                val database = SqliteHistoryDatabase.open(path)
                val firstId = first.matchId().toString()
                val secondId = second.matchId().toString()
                val replayIds = database.readReplayIds()
                assertEquals(1, replayIds.size, "The max-matches policy should retain one replay.")
                assertTrue(secondId in replayIds, "The newest replay should remain stored.")
                assertTrue(firstId !in replayIds, "The oldest replay should be pruned.")
                assertTrue(firstId in database.readTombstones(), "Pruning must leave a durable tombstone.")
                assertTrue(firstId !in database.readGaps(), "Pruning must not create a sequence gap.")
                assertTrue(liveStore.snapshot().games.isEmpty(), "Retention must not create live games.")
                assertTrue(liveStore.snapshot().rooms.isEmpty(), "Retention must not create live rooms.")
            } finally {
                writer.detach()
            }
        }
    }

    /** 驗證轉移只確認 writer 實際接受的事件，未保存批次仍留在隔離來源 outbox。 */
    @Test
    fun `partial generation transfer acknowledges only events accepted by writer`() = runBlocking {
        val runtime = runtime()
        val liveStore = liveStore()
        val path = createTempDirectory("mahjongcraft-history-partial-").resolve("history.sqlite")
        val writer = writer(liveStore)
        try {
            writer.attach(path)
            val session = checkNotNull(writer.currentSessionId)
            val game = checkNotNull(runtime.currentGame())
            assertTrue((writer.beginGeneration(game, session) as HistoryManagementResult.Success).value)

            val before = runtime.store.snapshot().historyRecordingState.pendingEvents
            assertTrue(before.isNotEmpty(), "A started generation should have a source event.")
            val first = before.first()
            val transaction = before.takeWhile { it.transactionFirstSequence == first.transactionFirstSequence }
            val result = writer.appendGeneration(first.matchId, transaction, session)
            val outcome = checkNotNull(result as? HistoryManagementResult.Success)
            assertEquals(HistoryTransferResult.ACCEPTED, outcome.value)
            assertTrue(liveStore.snapshot().historyRecordingState.pendingEvents.isEmpty(), "Writer acknowledgement must follow SQLite commit.")
            runtime.store.acknowledgeHistoryEvents(transaction.map { it.matchId to it.sequence }.toSet())
            assertTrue(
                runtime.store.snapshot().historyRecordingState.pendingEvents.none { it.sequence == first.sequence },
                "The source event may be removed only after writer acceptance.",
            )
        } finally {
            writer.detach()
        }
    }

    /** 驗證 writer 中止時會清除轉移並保留不可完成的中止證據。 */
    @Test
    fun `detaching active generation marks interrupted transfer`() = runBlocking {
        val runtime = runtime()
        val liveStore = liveStore()
        val path = createTempDirectory("mahjongcraft-history-detach-").resolve("history.sqlite")
        val writer = writer(liveStore)
        var detached = false
        try {
            writer.attach(path)
            val session = checkNotNull(writer.currentSessionId)
            val game = checkNotNull(runtime.currentGame())
            val result = checkNotNull(writer.beginGeneration(game, session) as? HistoryManagementResult.Success)
            assertTrue(result.value, "The active generation must be accepted before detaching.")
            assertTrue(
                liveStore.snapshot().historyRecordingState.transfersByMatchId.containsKey(game.matchId),
                "The generation must remain active until detach.",
            )

            writer.detach()
            detached = true

            val recording = liveStore.snapshot().historyRecordingState
            assertTrue(recording.transfersByMatchId.isEmpty(), "Detach must remove active transfer metadata.")
            val database = SqliteHistoryDatabase.open(path)
            assertTrue(game.matchId.toString() in database.readTerminalPartialIds(), "Interrupted terminal evidence must be durably saved as partial.")
            assertEquals("PARTIAL_TRANSFER_INTERRUPTED", database.readRecordingStops()[game.matchId.toString()], "Detach must durably preserve the interrupted-transfer decision.")
            assertTrue(database.readReplayIds().isEmpty(), "An interrupted transfer must not become a complete replay.")
        } finally {
            if (!detached) writer.detach()
        }
    }

    /** 建立包含正式 Flow 與內建 AI 策略的隔離四 AI 對局。 */
    private suspend fun runtime(): HeadlessFlowHistoryRuntime {
        val modules = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val strategies = MahjongAiStrategyRegistryImpl(BuiltInAiStrategyKeys.BEGINNER).apply {
            registerBuiltInAiStrategies(modules, ExtensionGameActionAiRegistry(modules), OpponentModelRegistry())
        }
        return HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, strategies)
    }

    /**
     * 建立使用正式 persistence registry 與 SQLite writer 的測試實例。
     *
     * @param store writer 使用的獨立 live store，不包含生成 runtime 的遊戲。
     * @param historyConfig 此測試 session 的歷史資格設定。
     * @return 尚未附加 SQLite 檔案的 writer。
     */
    private fun writer(
        store: AuthoritativeStateStore,
        historyConfig: MinecraftHistoryConfig = MinecraftHistoryConfig(includeAiMatches = true),
    ): FabricHistoryOutboxWriter = FabricHistoryOutboxWriter(
        store = store,
        registries = buildBuiltInPersistenceRegistries(),
        replayProjectionRegistry = buildTestHistoryReplayProjectionRegistry(),
        json = Json,
        dispatchers = TestDispatchers,
        moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() },
        locations = TableLocationRegistry(),
        configState = MinecraftServerConfigState(MinecraftServerConfig(history = historyConfig)),
        retentionCoordinator = HistoryRetentionCoordinator(),
    )

    /**
     * 將 runner 批次交給正式 writer，僅回傳已由 writer 寫入 SQLite 並從 live store 排空的事件識別碼。
     *
     * @param writer 正式歷史 outbox writer。
     * @param liveStore writer 所屬的獨立 live store。
     * @param connection 僅查詢本批穩定序號的驗證連線，不讀取整場 payload。
     * @param progress 生成 runtime 發出的不可變批次。
     * @param session writer session 識別碼。
     * @return 可由來源 runtime 確認移除的事件識別碼。
     */
    private suspend fun transfer(
        writer: FabricHistoryOutboxWriter,
        liveStore: AuthoritativeStateStore,
        connection: Connection,
        progress: HeadlessHistoryProgress,
        session: Uuid,
    ): Set<Pair<Uuid, Long>> {
        if (progress.events.isEmpty()) {
            if (progress.terminal) {
                val terminal = progress.snapshot.historyRecordingState.terminalByMatchId[progress.matchId]
                check(terminal?.completed == true) { "Completed generation must provide terminal proof." }
                val result = writer.finishGeneration(
                    progress.matchId,
                    terminal,
                    session,
                )
                check(result is HistoryManagementResult.Success) { "Terminal evidence must be durably archived." }
                check(result.value.replayBytes[progress.matchId.toString()] != null) { "Completed replay must be present." }
                check(progress.matchId.toString() !in result.value.pruned) { "Completed replay must not be pruned immediately." }
            }
            return emptySet()
        }
        var result: HistoryManagementResult<HistoryTransferResult>
        do {
            result = writer.appendGeneration(progress.matchId, progress.events, session)
            if (result is HistoryManagementResult.Success && result.value == HistoryTransferResult.ACCEPTED) break
            if (result is HistoryManagementResult.Success && result.value == HistoryTransferResult.STOPPED) return emptySet()
            delay(10.milliseconds)
        } while (true)
        check(
            liveStore.snapshot().historyRecordingState.pendingEvents.none { event ->
                progress.events.any { it.matchId == event.matchId && it.sequence == event.sequence }
            },
        ) { "Source acknowledgement must follow the writer's durable SQLite commit." }
        connection.prepareStatement("SELECT COUNT(*) FROM history_pending_event WHERE match_id = ? AND sequence BETWEEN ? AND ?").use { statement ->
            statement.setString(1, progress.matchId.toString())
            statement.setLong(2, progress.events.first().sequence)
            statement.setLong(3, progress.events.last().sequence)
            statement.executeQuery().use { rows ->
                check(rows.next() && rows.getInt(1) == progress.events.size) { "Every acknowledged event must have a durable SQLite record." }
            }
        }
        if (progress.terminal) {
            val terminal = progress.snapshot.historyRecordingState.terminalByMatchId[progress.matchId]
            check(terminal?.completed == true) { "Completed generation must provide terminal proof." }
            val finish = writer.finishGeneration(
                progress.matchId,
                terminal,
                session,
            )
            check(finish is HistoryManagementResult.Success) { "Terminal evidence must be durably archived." }
            check(finish.value.replayBytes[progress.matchId.toString()] != null) { "Completed replay must be present." }
            check(progress.matchId.toString() !in finish.value.pruned) { "Completed replay must not be pruned immediately." }
        }
        return progress.events.map { it.matchId to it.sequence }.toSet()
    }

    /** 建立不含任何正式遊戲或房間的有界 live writer store。 */
    private fun liveStore(): AuthoritativeStateStore = AuthoritativeStateStore(
        historyRecordingEnabled = true,
        maxPendingHistoryEvents = 256,
    )

    /** 測試使用的非阻塞 dispatcher 邊界。 */
    private object TestDispatchers : CoroutineDispatchers {
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
        override val main = Dispatchers.Default
    }
}
