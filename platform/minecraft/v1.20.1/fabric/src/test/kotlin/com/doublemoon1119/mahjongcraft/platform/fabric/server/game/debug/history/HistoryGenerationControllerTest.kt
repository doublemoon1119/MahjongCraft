package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessFlowHistoryRuntime
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryRetentionCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.buildTestHistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledHeadlessHistoryRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 驗證歷史生成控制器的政策前置拒絕與 session 邊界。 */
class HistoryGenerationControllerTest {
    /** 設定停用時不建立隔離 runtime，也不啟動生成工作。 */
    @Test
    fun `disabled policy rejects before runtime creation`() = runBlocking {
        val store = AuthoritativeStateStore()
        val config = MinecraftServerConfigState(MinecraftServerConfig(history = MinecraftHistoryConfig(enabled = false, includeAiMatches = true)))
        val writer = writer(store, config)
        val path = createTempDirectory("history-controller-").resolve("history.sqlite")
        writer.attach(path)
        try {
            val scope = TestAppScope()
            val controller = HistoryGenerationController(
                writer = writer,
                runtimeFactory = HistoryGenerationRuntimeFactory { error("runtime must not be created") },
                store = store,
                configState = config,
                scope = scope,
                dispatchers = TestDispatchers,
            )
            assertEquals(
                HistoryGenerationStartResult.PolicyRejected,
                controller.start("mahjongcraft:riichi_east", 1),
            )
            assertTrue(controller.snapshot() == null)
        } finally {
            writer.detach()
        }
    }

    /** 第二次開始要求回報 busy，取消只在第一場完成後停止批次。 */
    @Test
    fun `busy and cancel stop after current real match`() = runBlocking {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val config = MinecraftServerConfigState(MinecraftServerConfig(history = MinecraftHistoryConfig(enabled = true, includeAiMatches = true)))
        val writer = writer(store, config)
        val path = createTempDirectory("history-controller-busy-").resolve("history.sqlite")
        writer.attach(path)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var creations = 0
        val scope = TestAppScope()
        val controller = HistoryGenerationController(
            writer = writer,
            runtimeFactory = HistoryGenerationRuntimeFactory { scenario ->
                creations++
                entered.complete(Unit)
                release.await()
                HeadlessFlowHistoryRuntime.create(scenario, bundledHeadlessHistoryRegistries())
            },
            store = store,
            configState = config,
            scope = scope,
            dispatchers = TestDispatchers,
        )
        try {
            assertEquals(HistoryGenerationStartResult.Started, controller.start("mahjongcraft:riichi_east", 2))
            withTimeout(5.seconds) { entered.await() }
            assertEquals(HistoryGenerationStartResult.Busy, controller.start("mahjongcraft:riichi_east", 1))
            assertTrue(controller.cancel())
            release.complete(Unit)
            withTimeout(55.seconds) {
                while (controller.snapshot()?.running != false) delay(50.milliseconds)
            }
            val progress = requireNotNull(controller.snapshot())
            assertEquals(1, creations)
            assertEquals(1, progress.generated)
            assertEquals(1, progress.archived)
            assertTrue(progress.cancelRequested)
            assertEquals(null, progress.failure)
        } finally {
            release.complete(Unit)
            scope.cancel()
            writer.detach()
            assertEquals(null, controller.snapshot())
        }
    }

    /** 建立與 production 相同的 writer 依賴邊界。
     * @param store 受測的權威歷史來源。
     * @param configState writer 使用的有效設定。
     * @return 未附加資料庫的歷史 writer。
     */
    private fun writer(
        store: AuthoritativeStateStore,
        configState: MinecraftServerConfigState,
    ): FabricHistoryOutboxWriter = FabricHistoryOutboxWriter(
        store = store,
        registries = bundledPersistenceRegistries(),
        replayProjectionRegistry = buildTestHistoryReplayProjectionRegistry(),
        json = Json,
        dispatchers = TestDispatchers,
        moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() },
        locations = TableLocationRegistry(),
        configState = configState,
        retentionCoordinator = HistoryRetentionCoordinator(),
    )

    /** 不需要額外生命週期的測試應用作用域。 */
    private class TestAppScope : AppCoroutineScope {
        /** 測試作用域的 coroutine context。 */
        override val coroutineContext: CoroutineContext = Job() + Dispatchers.Default

        /** 取消測試作用域中的所有工作。 */
        override fun cancel() {
            coroutineContext[Job]?.cancel()
        }

        /** 在測試中立即結束作用域。 */
        override suspend fun shutdown(timeoutMillis: Long) {
            coroutineContext[Job]?.cancel()
        }
    }

    /** 測試使用的 dispatcher 集合。 */
    private object TestDispatchers : CoroutineDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Default
    }
}
