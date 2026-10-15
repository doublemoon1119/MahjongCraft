package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScopeImpl
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.observer.model.ObserverSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverAudienceSource
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.HandReadinessVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.observer.ObserverSnapshotBroadcaster
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricServerStopRunner.StopStep
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** [FabricServerStopRunner] 的關服收尾等待測試；以單一執行緒模擬伺服器主執行緒。 */
class FabricServerStopRunnerTest {
    /** 模擬伺服器主執行緒的單一執行緒。 */
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fake-server-main").apply { isDaemon = true }
    }

    /** 模擬主執行緒的執行緒實例。 */
    private val mainThread: Thread = executor.submit<Thread> { Thread.currentThread() }.get()

    /** 排給模擬主執行緒的工作佇列。 */
    private val queue = MainThreadTaskQueue()

    /** 比照伺服器排程器：已在主執行緒時直接執行，否則排入佇列並請主執行緒處理。 */
    private val mainDispatcher = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean = Thread.currentThread() !== mainThread

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.enqueue(block)
            executor.execute { queue.drain() }
        }
    }

    /** 受測的關服收尾執行器。 */
    private val runner = FabricServerStopRunner(mainDispatcher = mainDispatcher, drain = queue)

    /** 關閉模擬主執行緒。 */
    @AfterTest
    fun shutdownExecutor() {
        executor.shutdownNow()
    }

    /**
     * 主執行緒上的廣播持有廣播鎖、排隊等權威狀態的鎖時開始關服：收尾在主執行緒處理工作，
     * 廣播得以完成並釋放廣播鎖，作用域關閉與清除廣播紀錄都在時限內完成。
     */
    @Test
    fun `stop cleanup completes while main-thread broadcasts still hold their lock`() {
        val id = Uuid.random()
        val hostId = Uuid.random()
        val store = AuthoritativeStateStore()
        runBlocking {
            store.update { state ->
                AuthoritativeStateUpdate(
                    state = state.copy(
                        rooms = state.rooms + (
                            id to Room(
                                id = id,
                                hostId = hostId,
                                gameConfig = GameConfig(FakeMahjongRuleConfig()),
                                playerIds = listOf(hostId),
                            )
                            ),
                    ),
                    result = Unit,
                )
            }
        }
        val sessionScope = AppCoroutineScopeImpl(dispatchers(mainDispatcher))
        val registry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val broadcaster = ObserverSnapshotBroadcaster(
            store = store,
            visibilityPolicy = GameVisibilityPolicyImpl(registry),
            handReadinessVisibilityPolicy = HandReadinessVisibilityPolicy(
                moduleRegistry = registry,
                visibilityPolicy = GameVisibilityPolicyImpl(registry),
            ),
            audienceSource = object : ObserverAudienceSource {
                override suspend fun observersById(): Map<Uuid, Set<Uuid>> = mapOf(id to setOf(hostId))
            },
            sender = object : ObserverSnapshotSender {
                override suspend fun send(
                    id: Uuid,
                    observerId: Uuid,
                    snapshot: ObserverSnapshot?,
                ) {
                    store.getRoom(id)
                }
            },
        )
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holderEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            background.launch {
                store.update { state ->
                    holderEntered.complete(Unit)
                    release.await()
                    AuthoritativeStateUpdate(state = state, result = Unit)
                }
            }
            runBlocking { holderEntered.await() }

            val unfinished = executor.submit<List<String>> {
                sessionScope.launch(mainDispatcher) { broadcaster.broadcast() }
                release.complete(Unit)
                runner.run(
                    steps = listOf(
                        StopStep("shutdown application scope") { sessionScope.shutdown(500.milliseconds) },
                        StopStep("stop observer broadcast") { broadcaster.clearAll() },
                    ),
                    timeout = 5.seconds,
                )
            }

            assertEquals(emptyList(), unfinished.get(5, TimeUnit.SECONDS))
        } finally {
            background.cancel()
        }
    }

    /** 收尾一直無法完成時在時限後放行，取消卡住的步驟，並回報它與之後未執行的步驟。 */
    @Test
    fun `stop cleanup gives up after the timeout`() {
        val log = mutableListOf<String>()

        val unfinished = executor.submit<List<String>> {
            runner.run(
                steps = listOf(
                    StopStep("first") { log += "first" },
                    StopStep("wait forever") {
                        try {
                            awaitCancellation()
                        } finally {
                            log += "cancelled"
                        }
                    },
                    StopStep("after") { log += "after" },
                ),
                timeout = 200.milliseconds,
            )
        }

        assertEquals(listOf("wait forever", "after"), unfinished.get(5, TimeUnit.SECONDS))
        assertEquals(listOf("first", "cancelled"), log)
    }

    /** 步驟依清單順序執行；單一步驟失敗只回報該步驟，之後的步驟照常執行。 */
    @Test
    fun `steps run in order and a failing step does not stop the rest`() {
        val log = mutableListOf<String>()

        val unfinished = executor.submit<List<String>> {
            runner.run(
                steps = listOf(
                    StopStep("first") { log += "first" },
                    StopStep("failing") { error("stop failure") },
                    StopStep("last") { log += "last" },
                ),
                timeout = 5.seconds,
            )
        }

        assertEquals(listOf("failing"), unfinished.get(5, TimeUnit.SECONDS))
        assertEquals(listOf("first", "last"), log)
    }

    /**
     * 建立以指定排程器作為主執行緒的 dispatcher 集合。
     *
     * @param main 模擬主執行緒的排程器。
     * @return 其餘工作使用 [Dispatchers.Default] 的 dispatcher 集合。
     */
    private fun dispatchers(main: CoroutineDispatcher): CoroutineDispatchers = object : CoroutineDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = main
        override val aiDecision: CoroutineDispatcher = Dispatchers.Default
        override val aiDecisionParallelism: Int = 1
    }
}
