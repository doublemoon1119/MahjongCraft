package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessFlowHistoryRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStage
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.SqliteHistoryDatabase
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.buildTestHistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledHeadlessHistoryRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** 驗證壓力測試資料環境依歷史處理方式處理共用權威來源中的對局歷史。 */
class StressTestEnvironmentTest {
    /** 寫入模式下，共用同一個權威來源的兩場三人對局都會寫進壓力測試資料庫且沒有遺失；刪除後資料庫檔案消失。 */
    @Test
    fun `matches sharing the stress store are archived to the stress database`() = runBlocking {
        val path = databasePath()
        val environment = assertNotNull(factory().openAt(path, StressHistoryMode.WRITE))
        try {
            val runtimes = playMatches(environment, count = 2)
            withTimeout(2.minutes) {
                val matchIds = runtimes.map { it.matchId().toString() }.toSet()
                while (!SqliteHistoryDatabase.open(path).readReplayIds().containsAll(matchIds)) delay(50.milliseconds)
            }
            assertEquals(emptyMap(), environment.store.snapshot().historyRecordingState.firstMissingSequenceByMatchId)
            assertNotNull(environment.historySource, "Writing to the database must let the history screen read the stress test records.")
            val stages = environment.writerTimer.stageSummaries()
            assertTrue(HistoryWriterStage.BATCH_WRITE in stages && HistoryWriterStage.ENCODE in stages, "Writing must time the batch stages, got $stages")
            assertTrue(environment.writerTimer.eventsWritten() > 0)
        } finally {
            environment.closeAndDelete()
        }
        assertFalse(path.exists(), "Deleting the stress environment must remove its database file.")
    }

    /** 只編碼模式下，所有事件編碼後就確認移除，結束場次的記錄狀態也一併清除，且不建立資料庫。 */
    @Test
    fun `encoding drains every event without a database`() = runBlocking {
        val path = databasePath()
        val environment = assertNotNull(factory().openAt(path, StressHistoryMode.ENCODE))
        try {
            playMatches(environment, count = 2)
            withTimeout(1.minutes) {
                while (environment.store.snapshot().historyRecordingState.let { it.pendingEvents.isNotEmpty() || it.terminalByMatchId.isNotEmpty() }) {
                    delay(20.milliseconds)
                }
            }
            val recording = environment.store.snapshot().historyRecordingState
            assertEquals(emptyMap(), recording.firstMissingSequenceByMatchId)
            assertEquals(emptyMap(), recording.nextSequenceByMatchId)
            assertEquals(setOf(HistoryWriterStage.ENCODE), environment.writerTimer.stageSummaries().keys)
            assertTrue(environment.writerTimer.eventsWritten() > 0)
            assertNull(environment.historySource, "Encoding only must not offer stress test records to the history screen.")
            assertFalse(environment.historyFailed())
        } finally {
            environment.closeAndDelete()
        }
        assertFalse(path.exists(), "Encoding only must not create a database file.")
    }

    /** 不記錄模式下，對局不產生任何待寫事件。 */
    @Test
    fun `history off records nothing`() = runBlocking {
        val environment = assertNotNull(factory().openAt(databasePath(), StressHistoryMode.OFF))
        try {
            playMatches(environment, count = 1)
            assertEquals(0, environment.stepTimer.historyEvents)
            assertTrue(environment.store.snapshot().historyRecordingState.pendingEvents.isEmpty())
            assertEquals(0, environment.writerTimer.eventsWritten())
            assertNull(environment.historySource)
        } finally {
            environment.closeAndDelete()
        }
    }

    /** 打完的桌移除後只剩下待處理的歷史；返回的房間不會留在共用權威來源。 */
    @Test
    fun `discarding a finished table removes its room`() = runBlocking {
        val environment = assertNotNull(factory().openAt(databasePath(), StressHistoryMode.OFF))
        try {
            val runtime = playMatches(environment, count = 1).single()
            assertTrue(runtime.venueId in environment.store.snapshot().rooms, "A finished table returns to its room.")

            discardStressTables(environment.store, listOf(runtime.venueId))

            val state = environment.store.snapshot()
            assertTrue(state.rooms.isEmpty() && state.games.isEmpty())
        } finally {
            environment.closeAndDelete()
        }
    }

    /** 卡住而移除的桌：對局與房間都移除，已記錄的歷史仍留在待寫佇列，並記為未完成的場次。 */
    @Test
    fun `discarding a running table keeps its pending history`() = runBlocking {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, bundledHeadlessHistoryRegistries(), store)
        repeat(RUNNING_STEPS) { runtime.step() }
        val matchId = runtime.matchId()
        val pending = store.snapshot().historyRecordingState.pendingEvents.count { it.matchId == matchId }

        discardStressTables(store, listOf(runtime.venueId))

        val state = store.snapshot()
        assertTrue(state.rooms.isEmpty() && state.games.isEmpty())
        assertEquals(pending, state.historyRecordingState.pendingEvents.count { it.matchId == matchId })
        assertEquals(false, state.historyRecordingState.terminalByMatchId[matchId]?.completed)
    }

    /** 停止測試時剩下的多桌以同一筆交易全部移除，各自記為未完成的場次。 */
    @Test
    fun `discarding several running tables ends every match`() = runBlocking {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val runtimes = List(2) { HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, bundledHeadlessHistoryRegistries(), store) }
        runtimes.forEach { runtime -> repeat(RUNNING_STEPS) { runtime.step() } }

        discardStressTables(store, runtimes.map { it.venueId })

        val state = store.snapshot()
        assertTrue(state.rooms.isEmpty() && state.games.isEmpty())
        runtimes.forEach { runtime -> assertEquals(false, state.historyRecordingState.terminalByMatchId[runtime.matchId()]?.completed) }
    }

    /** 在 [environment] 中同時打完 [count] 場三人對局；待寫事件太多時等待背景工作追上，模擬伺服器 tick 之間的時間。 */
    private suspend fun playMatches(environment: StressTestEnvironment, count: Int) = withTimeout(2.minutes) {
        val runtimes = List(count) {
            HeadlessFlowHistoryRuntime.create(
                scenario = HeadlessHistoryScenario.THREE_PLAYER_RIICHI_EAST,
                registries = bundledHeadlessHistoryRegistries(),
                store = environment.store,
                stepTimer = environment.stepTimer,
            )
        }
        while (runtimes.any { it.currentGame() != null }) {
            runtimes.filter { it.currentGame() != null }.forEach { runtime -> check(runtime.step()) { "Stress test match made no progress" } }
            while (environment.store.snapshot().historyRecordingState.pendingEvents.size > BACKLOG_PAUSE) delay(10.milliseconds)
        }
        runtimes
    }

    /** 新暫存資料夾中的資料庫位置。 */
    private fun databasePath(): Path = createTempDirectory("mahjongcraft-stress-test-").resolve("stress-test-history.sqlite")

    /** 以測試用 registry 建立環境工廠。 */
    private fun factory() = StressTestEnvironmentFactory(
        registries = bundledPersistenceRegistries(),
        json = Json,
        dispatchers = TestDispatchers,
        moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() },
        locations = TableLocationRegistry(),
        replayProjectionRegistry = buildTestHistoryReplayProjectionRegistry(),
    )

    /** 測試使用的 dispatcher 集合。 */
    private object TestDispatchers : CoroutineDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Default
        override val aiDecision: CoroutineDispatcher = Dispatchers.Default
        override val aiDecisionParallelism = 1
    }

    private companion object {
        /** 待寫事件超過這個數量時等待背景工作追上。 */
        const val BACKLOG_PAUSE = 64

        /** 移除進行中桌子前推進的步數。 */
        const val RUNNING_STEPS = 10
    }
}
