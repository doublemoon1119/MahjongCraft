package com.doublemoon1119.mahjongcraft.platform.minecraft.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MahjongCraftEvent
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchStartedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** JVM 併發提交時，事件仍可安全加入交付佇列。 */
class GameEventDeliveryJvmTest {
    /** 併發提交在交易序號受保護時，釋放後依序交付。 */
    @Test
    fun `concurrent submissions are retained before release`() {
        val firstGame = game()
        val secondGame = game()
        val registry = MahjongModuleRegistryImpl().apply {
            register(RiichiRuleConfig::class, RULE_MODULE_ID) { config, id -> RiichiRuleModule(id, config) }
        }
        val endpoint = MahjongCraftEvent.create<MatchStartedListener>()
        val received = Collections.synchronizedList(mutableListOf<Uuid>())
        val mainThread = Thread.currentThread()
        val callbackThreads = mutableListOf<Thread>()
        endpoint.register(
            MatchStartedListener { event, _ ->
                received += event.matchId
                callbackThreads += Thread.currentThread()
            },
        )
        val scheduler = QueueScheduler()
        val delivery = GameEventDelivery(
            projector = GameEventProjector(registry),
            scheduler = scheduler,
            locations = GameEventLocationSource { TableLocation("minecraft:overworld", 0, 64, 0) },
            reporter = NoopReporter(),
            matchStarted = endpoint,
            roundSettled = MahjongCraftEvent.create(),
            matchEnded = MahjongCraftEvent.create(),
        )
        val session = delivery.startSession()
        val firstFacts = startFacts(firstGame)
        val secondFacts = startFacts(secondGame)
        val ready = CountDownLatch(1)
        val commitLock = Any()
        var nextSequence = 1L
        val expected = mutableListOf<Uuid>()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit {
                check(ready.await(5, TimeUnit.SECONDS))
                synchronized(commitLock) {
                    expected += firstGame.matchId
                    delivery.onCommitted(session, nextSequence++, firstFacts)
                }
            }
            val second = pool.submit {
                check(ready.await(5, TimeUnit.SECONDS))
                synchronized(commitLock) {
                    expected += secondGame.matchId
                    delivery.onCommitted(session, nextSequence++, secondFacts)
                }
            }
            ready.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            assertEquals(0, scheduler.size())
            val releaseReady = CountDownLatch(1)
            val releaseFirst = pool.submit {
                check(releaseReady.await(5, TimeUnit.SECONDS))
                delivery.onReleased(session, 1)
            }
            val releaseSecond = pool.submit {
                check(releaseReady.await(5, TimeUnit.SECONDS))
                delivery.onReleased(session, 2)
            }
            releaseReady.countDown()
            releaseFirst.get(5, TimeUnit.SECONDS)
            releaseSecond.get(5, TimeUnit.SECONDS)
            scheduler.runAll()
            assertEquals(expected, received)
            assertEquals(listOf(mainThread, mainThread), callbackThreads)
        } finally {
            pool.shutdownNow()
        }
    }

    /** 建立一筆對局開始事實。 */
    private fun startFacts(game: Game): CommittedGameFacts = CommittedGameFacts(
        venueId = game.id,
        previousGame = null,
        game = game,
        facts = listOf(HistoryEventDraft(null, HistoryFact.MatchStarted(game.tableState, GameFlowConfig(), emptyMap()))),
    )

    /** 可由測試手動執行的排程器。 */
    private class QueueScheduler : GameEventScheduler {
        /** 可由背景通知安全排入的交付工作。 */
        private val tasks = Collections.synchronizedList(mutableListOf<() -> Unit>())

        override fun enqueue(task: () -> Unit) {
            tasks += task
        }

        /** 取得尚未執行的工作數。 */
        fun size(): Int = synchronized(tasks) { tasks.size }

        /** 在目前測試主執行緒執行所有已排程工作。 */
        fun runAll() {
            while (true) {
                val task = synchronized(tasks) {
                    if (tasks.isEmpty()) null else tasks.removeAt(0)
                } ?: return
                task()
            }
        }
    }

    /** 不記錄診斷的回報器。 */
    private class NoopReporter : GameEventDeliveryReporter {
        override fun missingLocation(matchId: Uuid, venueId: Uuid) = Unit
        override fun listenerFailed(event: MatchEvent, cause: Throwable) = Unit
        override fun schedulingFailed(cause: Throwable) = Unit
    }

    /** 建立測試用四人日麻對局。 */
    private fun game(): Game = Game(
        tableState = FakeTableStateFactory.create(
            id = Uuid.random(),
            players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map {
                FakeMahjongPlayerFactory.create(initialSeat = it)
            },
            config = RiichiRuleConfig(),
        ),
        flowConfig = GameFlowConfig(),
        matchId = Uuid.random(),
    )

    private companion object {
        /** 測試規則模組 ID。 */
        const val RULE_MODULE_ID = "mahjongcraft:riichi"
    }
}
