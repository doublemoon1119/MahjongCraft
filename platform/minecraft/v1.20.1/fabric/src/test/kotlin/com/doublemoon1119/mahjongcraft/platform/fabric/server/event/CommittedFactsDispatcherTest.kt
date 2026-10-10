package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.api.event.FabricGameEventContexts
import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.AchievementGrantGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.FabricAchievementService
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.PlayerAchievements
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.GameEventContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchEndedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchStartedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MinecraftMatchAbortReasonIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.RoundSettledListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventDeliveryReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventScheduler
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventSubscriptions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.minecraft.server.MinecraftServer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** 驗證 Fabric 橋接將背景提交交給主執行緒佇列，並隔離舊 session。 */
class CommittedFactsDispatcherTest {
    /** 背景交易提交的三種事件都排入佇列，交付時可重新查詢權威狀態；舊 session 事件會被捨棄。 */
    @Test
    fun `background commits are queued and stale session events are discarded`() = verifyBridgeSession()

    /** 同一 JVM 先後執行獨立橋接時，各自可註冊且不會收到另一份訂閱點的通知。 */
    @Test
    fun `independent bridges can register and deliver repeatedly in the same JVM`() {
        repeat(2) { verifyBridgeSession() }
    }

    /** 建立獨立訂閱點並驗證完整橋接流程；離開時關閉 session 與背景執行器。 */
    private fun verifyBridgeSession() = runBlocking {
        val store = AuthoritativeStateStore()
        val repository = GameRepositoryImpl(store)
        val scheduler = QueueScheduler()
        val subscriptions = GameEventSubscriptions()
        val activeGame = game()
        val locations = TableLocationRegistry()
        val activeLocation = TableLocation("minecraft:overworld", 4, 8, 12)
        locations.put(activeGame.id, activeLocation)
        val changedGame = activeGame.copy(tableState = activeGame.tableState.copy(currentPlayerIndex = 1))
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val exclusions = GameEventExclusions()
        val reporter = RecordingReporter()
        val serverProvider: () -> MinecraftServer = {
            throw UnsupportedOperationException("The test does not construct a Minecraft server")
        }
        val gateway = RecordingGateway()
        val dispatcher = CommittedFactsDispatcher(
            store = store,
            achievementService = achievementService(moduleRegistry, exclusions, gateway),
            moduleRegistry = moduleRegistry,
            locations = FabricGameEventLocationSource(locations),
            reporter = reporter,
            exclusions = exclusions,
        )
        val received = mutableListOf<String>()
        var observedGame: Game? = null
        var observedContext: GameEventContext? = null
        var callbackThread: Thread? = null
        val ownerThread = Thread.currentThread()
        var lastEndedContext: GameEventContext? = null
        subscriptions.matchStarted.register(
            MatchStartedListener { _, context ->
                received += "started"
                callbackThread = Thread.currentThread()
                observedContext = context
                observedGame = runBlocking { withTimeout(5.seconds) { store.getGame(activeGame.id) } }
            },
        )
        subscriptions.roundSettled.register(RoundSettledListener { _, _ -> received += "settled" })
        subscriptions.matchEnded.register(
            MatchEndedListener { _, context ->
                received += "ended"
                lastEndedContext = context
            },
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val preSessionGame = game()
            repository.updateGame(
                gameId = preSessionGame.id,
                history = { _, _, _ ->
                    listOf(HistoryEventDraft(null, HistoryFact.MatchStarted(preSessionGame.tableState, preSessionGame.flowConfig, emptyMap())))
                },
            ) { preSessionGame to Unit }
            repository.updateGame(
                gameId = preSessionGame.id,
                history = { _, _, _ ->
                    listOf(HistoryEventDraft(null, HistoryFact.MatchAborted(MinecraftMatchAbortReasonIds.TABLE_MISSING)))
                },
            ) { null to Unit }
            dispatcher.startSession(scheduler = scheduler, serverProvider = serverProvider, subscriptions = subscriptions)
            val oldGame = game()
            submit(executor) {
                repository.updateGame(
                    gameId = oldGame.id,
                    history = { _, _, _ ->
                        listOf(
                            HistoryEventDraft(
                                null,
                                HistoryFact.MatchStarted(oldGame.tableState, oldGame.flowConfig, oldGame.aiPlayerStrategyKeys),
                            ),
                        )
                    },
                ) { oldGame to Unit }
            }.get(5, TimeUnit.SECONDS)
            dispatcher.stopSession()
            dispatcher.startSession(scheduler = scheduler, serverProvider = serverProvider, subscriptions = subscriptions)
            scheduler.runAll()
            assertTrue(received.isEmpty())

            runBlocking {
                repository.updateGame(
                    gameId = activeGame.id,
                    history = { _, _, _ ->
                        listOf(
                            HistoryEventDraft(
                                null,
                                HistoryFact.MatchStarted(activeGame.tableState, activeGame.flowConfig, activeGame.aiPlayerStrategyKeys),
                            ),
                        )
                    },
                ) { activeGame to Unit }
            }
            submit(executor) {
                repository.updateGame(
                    gameId = activeGame.id,
                    history = { _, _, _ ->
                        listOf(
                            HistoryEventDraft(
                                null,
                                HistoryFact.WinSettled(
                                    outcomeId = BuiltInRoundOutcomeIds.TSUMO,
                                    winDetails = listOf(HistoryWinDetails(activeGame.tableState.players.first().id, emptyList())),
                                ),
                            ),
                            HistoryEventDraft(
                                null,
                                HistoryFact.MatchCompleted("mahjongcraft:completed", emptyMap()),
                            ),
                        )
                    },
                ) { changedGame to Unit }
            }.get(5, TimeUnit.SECONDS)

            assertEquals(1, scheduler.size())
            assertTrue(received.isEmpty())
            scheduler.runAll()

            assertEquals(listOf("started", "settled", "ended"), received)
            assertEquals(changedGame, observedGame)
            assertEquals(ownerThread, callbackThread)
            val capturedContext = assertNotNull(observedContext)
            assertEquals(1, reporter.missingLocations.size)
            assertTrue(gateway.granted.isNotEmpty())
            assertFailsWith<UnsupportedOperationException> { FabricGameEventContexts.server(capturedContext) }

            val abortedGame = changedGame.copy(matchId = Uuid.random())
            val removalLocationEntry = locations.put(abortedGame.id, activeLocation)
            repository.updateGame(
                gameId = abortedGame.id,
                history = { _, _, _ ->
                    listOf(
                        HistoryEventDraft(
                            null,
                            HistoryFact.MatchStarted(abortedGame.tableState, abortedGame.flowConfig, emptyMap()),
                        ),
                    )
                },
            ) { abortedGame to Unit }
            scheduler.runAll()
            repository.updateGame(
                gameId = abortedGame.id,
                history = { _, _, _ ->
                    listOf(HistoryEventDraft(null, HistoryFact.MatchAborted(MinecraftMatchAbortReasonIds.TABLE_MISSING)))
                },
            ) { null to Unit }
            locations.remove(abortedGame.id, removalLocationEntry.revision)
            scheduler.runAll()
            val endedLocation = assertNotNull(lastEndedContext?.tableLocation)
            assertEquals(activeLocation.dimensionId, endedLocation.dimensionId)
            assertEquals(activeLocation.x, endedLocation.x)
            assertEquals(activeLocation.y, endedLocation.y)
            assertEquals(activeLocation.z, endedLocation.z)
            val receivedBeforeExclusion = received.toList()
            val grantsBeforeExclusion = gateway.granted.size
            exclusions.exclude(activeGame.matchId)
            val excludedGame = changedGame.copy(tableState = changedGame.tableState.copy(comboCount = 1))
            submit(executor) {
                repository.updateGame(
                    gameId = activeGame.id,
                    history = { _, _, _ ->
                        listOf(
                            HistoryEventDraft(
                                null,
                                HistoryFact.WinSettled(
                                    outcomeId = BuiltInRoundOutcomeIds.TSUMO,
                                    winDetails = listOf(HistoryWinDetails(activeGame.tableState.players.first().id, emptyList())),
                                ),
                            ),
                        )
                    },
                ) { excludedGame to Unit }
            }.get(5, TimeUnit.SECONDS)
            assertEquals(0, scheduler.size())
            scheduler.runAll()
            assertEquals(receivedBeforeExclusion, received)
            assertEquals(grantsBeforeExclusion, gateway.granted.size)
            dispatcher.stopSession()
            assertFalse(exclusions.contains(activeGame.matchId))
            assertFailsWith<IllegalStateException> { FabricGameEventContexts.server(capturedContext) }
            dispatcher.startSession(scheduler = scheduler, serverProvider = serverProvider, subscriptions = subscriptions)
            assertFailsWith<IllegalStateException> { FabricGameEventContexts.server(capturedContext) }
            dispatcher.stopSession()
            assertTrue(reporter.listenerFailures.isEmpty())
            assertTrue(reporter.schedulingFailures.isEmpty())
        } finally {
            dispatcher.stopSession()
            executor.shutdownNow()
        }
    }

    /**
     * 建立測試用成果服務；事件橋接測試只驗證事件交付，不驗證成果內容。
     *
     * @param moduleRegistry 測試用規則模組 registry。
     * @param exclusions 與事件分派器共用的排除名單。
     * @param gateway 接收成果授予的測試 gateway。
     */
    private fun achievementService(
        moduleRegistry: MahjongModuleRegistryImpl,
        exclusions: GameEventExclusions,
        gateway: AchievementGrantGateway,
    ): FabricAchievementService {
        val dispatchers = TestCoroutineDispatchers()
        return FabricAchievementService(
            scope = createTestAppCoroutineScope(dispatchers),
            dispatchers = dispatchers,
            moduleRegistry = moduleRegistry,
            resolverRegistry = GameAchievementResolverRegistryImpl(),
            gateway = gateway,
            exclusions = exclusions,
        )
    }

    /** 建立測試用四人日麻對局。 */
    private fun game(): Game = Game(
        tableState = FakeTableStateFactory.create(
            players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map {
                FakeMahjongPlayerFactory.create(initialSeat = it)
            },
            config = RiichiRuleConfig(),
        ),
        flowConfig = GameFlowConfig(),
        matchId = Uuid.random(),
    )

    /** 可由測試手動執行的主執行緒排程器。 */
    private class QueueScheduler : GameEventScheduler {
        /** 尚未執行的主執行緒工作。 */
        private val tasks = ConcurrentLinkedQueue<() -> Unit>()

        override fun enqueue(task: () -> Unit) {
            tasks += task
        }

        fun size(): Int = tasks.size

        fun runAll() {
            while (true) tasks.poll()?.invoke() ?: return
        }
    }

    /** 記錄事件橋接測試中的診斷回報。 */
    private class RecordingReporter : GameEventDeliveryReporter {
        /** 找不到桌位的事件識別。 */
        val missingLocations = mutableListOf<Pair<Uuid, Uuid>>()

        /** 監聽者失敗診斷。 */
        val listenerFailures = mutableListOf<Throwable>()

        /** 排程失敗診斷。 */
        val schedulingFailures = mutableListOf<Throwable>()

        override fun missingLocation(matchId: Uuid, venueId: Uuid) {
            missingLocations += matchId to venueId
        }

        override fun listenerFailed(event: MatchEvent, cause: Throwable) {
            listenerFailures += cause
        }

        override fun schedulingFailed(cause: Throwable) {
            schedulingFailures += cause
        }
    }

    /** 記錄成果服務交付的玩家成果。 */
    private class RecordingGateway : AchievementGrantGateway {
        /** 已交付給測試 gateway 的成果批次。 */
        val granted = mutableListOf<PlayerAchievements>()

        override fun grant(
            achievements: PlayerAchievements,
        ): Boolean {
            granted += achievements
            return true
        }
    }

    /** 將 suspend 交易提交到背景執行緒。
     *
     * @param executor 接收背景工作的執行器。
     * @param block 欲提交的權威交易。
     * @return 可等待交易完成的 future。
     */
    private fun submit(executor: ExecutorService, block: suspend () -> Unit): Future<*> = executor.submit { runBlocking { block() } }
}
