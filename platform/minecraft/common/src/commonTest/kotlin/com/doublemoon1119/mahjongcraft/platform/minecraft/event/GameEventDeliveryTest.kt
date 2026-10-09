package com.doublemoon1119.mahjongcraft.platform.minecraft.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStartedEvent
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
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.GameEventContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MahjongCraftEvent
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchEndedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchStartedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.RoundSettledListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [GameEventDelivery] 僅在交易釋放後，依序把投影事件交付給訂閱者。 */
class GameEventDeliveryTest {
    /** 用於建立真實投影事件的規則模組登錄。 */
    private val registry = MahjongModuleRegistryImpl().apply {
        register(RiichiRuleConfig::class, RULE_MODULE_ID) { config, id -> RiichiRuleModule(id, config) }
    }

    /** 測試用的固定對局。 */
    private val game = game()

    /** 第一次啟動鎖定三種訂閱點，關閉與重新啟動都不會重新開放註冊。 */
    @Test
    fun `starting a session permanently locks all injected endpoints`() {
        val started = MahjongCraftEvent.create<MatchStartedListener>()
        val settled = MahjongCraftEvent.create<RoundSettledListener>()
        val ended = MahjongCraftEvent.create<MatchEndedListener>()
        val delivery = GameEventDelivery(
            projector = GameEventProjector(registry),
            scheduler = ManualScheduler(),
            locations = GameEventLocationSource { null },
            reporter = RecordingReporter(),
            matchStarted = started,
            roundSettled = settled,
            matchEnded = ended,
        )
        started.register(MatchStartedListener { _, _ -> })
        settled.register(RoundSettledListener { _, _ -> })
        ended.register(MatchEndedListener { _, _ -> })
        val session = delivery.startSession()

        assertFailsWith<IllegalStateException> { started.register(MatchStartedListener { _, _ -> }) }
        assertFailsWith<IllegalStateException> { settled.register(RoundSettledListener { _, _ -> }) }
        assertFailsWith<IllegalStateException> { ended.register(MatchEndedListener { _, _ -> }) }
        delivery.stopSession(session)
        assertFailsWith<IllegalStateException> { started.register(MatchStartedListener { _, _ -> }) }
        delivery.startSession()
        assertFailsWith<IllegalStateException> { started.register(MatchStartedListener { _, _ -> }) }
        assertEquals(1, started.listenersSnapshot().size)
        assertEquals(1, settled.listenersSnapshot().size)
        assertEquals(1, ended.listenersSnapshot().size)
    }

    /** 交易尚未釋放時不會交付事件。 */
    @Test
    fun `event waits until transaction is released and is delivered`() {
        val scheduler = ManualScheduler()
        val received = mutableListOf<Uuid>()
        val endpoint = MahjongCraftEvent.create<MatchStartedListener>().also {
            it.register(MatchStartedListener { event, _ -> received += event.matchId })
        }
        val delivery = delivery(scheduler, endpoint)
        val session = delivery.startSession()

        delivery.onCommitted(session, 1, startFacts())
        assertTrue(scheduler.tasks.isEmpty())

        delivery.onReleased(session, 1)
        assertEquals(1, scheduler.tasks.size)
        assertTrue(received.isEmpty())

        scheduler.runNext()
        assertEquals(listOf(game.matchId), received)
    }

    /** 缺少桌位仍交付事件並回報診斷。 */
    @Test
    fun `events are delivered when location is missing`() {
        val scheduler = ManualScheduler()
        val reporter = RecordingReporter()
        val contexts = mutableListOf<GameEventContext>()
        val endpoint = MahjongCraftEvent.create<MatchStartedListener>().also {
            it.register(MatchStartedListener { _, context -> contexts += context })
        }
        val delivery = delivery(scheduler, endpoint, locations = GameEventLocationSource { null }, reporter = reporter)
        val session = delivery.startSession()

        delivery.onCommitted(session, 1, startFacts())
        delivery.onReleased(session, 1)
        scheduler.runNext()

        assertEquals(listOf(game.matchId to game.id), reporter.missingLocations)
        assertNull(contexts.single().tableLocation)
    }

    /** 三種訂閱點依同一交易的投影順序交付，且共用提交時擷取的情境。 */
    @Test
    fun `all event types are delivered in projection order`() {
        val scheduler = ManualScheduler()
        val received = mutableListOf<String>()
        val contexts = mutableListOf<GameEventContext>()
        val started = endpoint { _, context ->
            received += "started"
            contexts += context
        }
        val settled = MahjongCraftEvent.create<RoundSettledListener>()
        settled.register(
            RoundSettledListener { _, context ->
                received += "settled"
                contexts += context
            },
        )
        val ended = MahjongCraftEvent.create<MatchEndedListener>()
        ended.register(
            MatchEndedListener { _, context ->
                received += "ended"
                contexts += context
            },
        )
        val delivery = GameEventDelivery(
            projector = GameEventProjector(registry),
            scheduler = scheduler,
            locations = GameEventLocationSource { TableLocation("minecraft:overworld", 0, 64, 0) },
            reporter = RecordingReporter(),
            matchStarted = started,
            roundSettled = settled,
            matchEnded = ended,
        )
        val facts = startFacts().copy(
            previousGame = game,
            facts = startFacts().facts + listOf(
                HistoryEventDraft(null, HistoryFact.WinSettled(outcomeId = "test:win", winDetails = emptyList())),
                HistoryEventDraft(null, HistoryFact.MatchCompleted(reasonId = "test:completed", finalScoresByPlayerId = emptyMap())),
            ),
        )
        val session = delivery.startSession()

        delivery.onCommitted(session, 1, facts)
        delivery.onReleased(session, 1)
        scheduler.runNext()

        assertEquals(listOf("started", "settled", "ended"), received)
        assertEquals(1, contexts.distinct().size)
        assertTrue(delivery.belongsToSession(contexts.first(), session))
        delivery.stopSession(session)
        assertFalse(delivery.belongsToSession(contexts.first(), session))
    }

    /** 單一監聽者失敗不會阻止同一事件的其他監聽者。 */
    @Test
    fun `listener failure does not prevent later listeners`() {
        val scheduler = ManualScheduler()
        val received = mutableListOf<Uuid>()
        val endpoint = MahjongCraftEvent.create<MatchStartedListener>().also {
            it.register(MatchStartedListener { _, _ -> error("test listener failure") })
            it.register(MatchStartedListener { event, _ -> received += event.matchId })
        }
        val reporter = RecordingReporter()
        val delivery = delivery(scheduler, endpoint, reporter = reporter)
        val session = delivery.startSession()

        delivery.onCommitted(session, 1, startFacts())
        delivery.onReleased(session, 1)
        scheduler.runNext()

        assertEquals(listOf(game.matchId), received)
        assertEquals(1, reporter.listenerFailures)
    }

    /** 舊 session 的排程工作會在執行時被捨棄。 */
    @Test
    fun `old session events and scheduled work are discarded`() {
        val scheduler = ManualScheduler()
        val received = mutableListOf<Uuid>()
        val endpoint = MahjongCraftEvent.create<MatchStartedListener>().also {
            it.register(MatchStartedListener { event, _ -> received += event.matchId })
        }
        val delivery = delivery(scheduler, endpoint)
        val oldSession = delivery.startSession()
        delivery.onCommitted(oldSession, 1, startFacts())
        delivery.onReleased(oldSession, 1)
        assertEquals(1, scheduler.tasks.size)

        val newSession = delivery.startSession()
        scheduler.runNext()
        assertTrue(received.isEmpty())
        assertFalse(delivery.belongsToSession(GameEventContext.create(null, oldSession), newSession))
    }

    /** 可交付水位只會前進，不會被較小的釋放序號退回。 */
    @Test
    fun `release watermark does not regress`() {
        val scheduler = ManualScheduler()
        val received = mutableListOf<Uuid>()
        val endpoint = endpoint { event, _ -> received += event.matchId }
        val delivery = delivery(scheduler, endpoint)
        val session = delivery.startSession()

        delivery.onCommitted(session, 1, startFacts())
        delivery.onCommitted(session, 2, startFacts())
        delivery.onReleased(session, 2)
        delivery.onReleased(session, 1)
        scheduler.runNext()

        assertEquals(2, received.size)
    }

    /** 尚未釋放的較後交易不可越過已交付的較前交易。 */
    @Test
    fun `later pending sequence waits for its release`() {
        val scheduler = ManualScheduler()
        val received = mutableListOf<Uuid>()
        val endpoint = endpoint { event, _ -> received += event.matchId }
        val delivery = delivery(scheduler, endpoint)
        val session = delivery.startSession()

        delivery.onCommitted(session, 1, startFacts())
        delivery.onReleased(session, 1)
        delivery.onCommitted(session, 2, startFacts())
        scheduler.runNext()

        assertEquals(1, received.size)
        delivery.onReleased(session, 2)
        scheduler.runNext()
        assertEquals(2, received.size)
    }

    /** 事件情境保存投影當下的位置，而不是交付工作執行時的位置。 */
    @Test
    fun `location is captured before the source changes`() {
        val scheduler = ManualScheduler()
        val received = mutableListOf<Int>()
        var location = TableLocation("minecraft:overworld", 10, 64, 10)
        val endpoint = MahjongCraftEvent.create<MatchStartedListener>().also {
            it.register(MatchStartedListener { _, context -> received += checkNotNull(context.tableLocation).x })
        }
        val delivery = delivery(scheduler, endpoint, locations = GameEventLocationSource { location })
        val session = delivery.startSession()

        delivery.onCommitted(session, 1, startFacts())
        location = TableLocation("minecraft:overworld", 20, 64, 20)
        delivery.onReleased(session, 1)
        scheduler.runNext()

        assertEquals(listOf(10), received)
    }

    /** 單一監聽者失敗後，該監聽者仍會收到後續事件。 */
    @Test
    fun `listener failure does not suppress later event`() {
        val scheduler = ManualScheduler()
        val received = mutableListOf<Uuid>()
        var first = true
        val endpoint = MahjongCraftEvent.create<MatchStartedListener>().also {
            it.register(
                MatchStartedListener { event, _ ->
                    if (first) {
                        first = false
                        error("test listener failure")
                    }
                    received += event.matchId
                },
            )
        }
        val reporter = RecordingReporter()
        val delivery = delivery(scheduler, endpoint, reporter = reporter)
        val session = delivery.startSession()

        delivery.onCommitted(session, 1, startFacts())
        delivery.onReleased(session, 1)
        scheduler.runNext()
        delivery.onCommitted(session, 2, startFacts())
        delivery.onReleased(session, 2)
        scheduler.runNext()

        assertEquals(listOf(game.matchId), received)
        assertEquals(1, reporter.listenerFailures)
    }

    /** 舊 session 的提交與釋放通知都不會影響新 session。 */
    @Test
    fun `old session callbacks are rejected after a new session starts`() {
        val scheduler = ManualScheduler()
        val received = mutableListOf<Uuid>()
        val endpoint = endpoint { event, _ -> received += event.matchId }
        val delivery = delivery(scheduler, endpoint)
        val oldSession = delivery.startSession()
        val newSession = delivery.startSession()

        delivery.stopSession(oldSession)
        delivery.onCommitted(oldSession, 1, startFacts())
        delivery.onReleased(oldSession, 1)
        delivery.onCommitted(newSession, 1, startFacts())
        delivery.onReleased(newSession, 1)
        scheduler.runNext()

        assertEquals(listOf(game.matchId), received)
    }

    /** 監聽者切換 session 時，舊事件剩餘監聽者不會繼續收到事件。 */
    @Test
    fun `starting a session from a listener suppresses remaining old listeners`() {
        val scheduler = ManualScheduler()
        var secondListenerCalled = false
        val endpoint = MahjongCraftEvent.create<MatchStartedListener>()
        lateinit var delivery: GameEventDelivery
        endpoint.register(MatchStartedListener { _, _ -> delivery.startSession() })
        endpoint.register(MatchStartedListener { _, _ -> secondListenerCalled = true })
        delivery = delivery(scheduler, endpoint)
        val session = delivery.startSession()

        delivery.onCommitted(session, 1, startFacts())
        delivery.onReleased(session, 1)
        scheduler.runNext()

        assertFalse(secondListenerCalled)
    }

    /** 監聽者重新提交並釋放交易時，事件仍依序在同一交付工作中完成。 */
    @Test
    fun `reentrant commit and release preserve order`() {
        val scheduler = ManualScheduler()
        val received = mutableListOf<Uuid>()
        val endpoint = MahjongCraftEvent.create<MatchStartedListener>()
        lateinit var delivery: GameEventDelivery
        var reentered = false
        lateinit var sessionId: Uuid
        endpoint.register(
            MatchStartedListener { event, _ ->
                received += event.matchId
                if (!reentered) {
                    reentered = true
                    delivery.onCommitted(sessionId, 2, startFacts())
                    delivery.onReleased(sessionId, 2)
                }
            },
        )
        delivery = delivery(scheduler, endpoint)
        sessionId = delivery.startSession()
        delivery.onCommitted(sessionId, 1, startFacts())
        delivery.onReleased(sessionId, 1)
        scheduler.runNext()

        assertEquals(listOf(game.matchId, game.matchId), received)
    }

    /** 排程失敗時事件保留，下一次可交付通知仍可重新排程。 */
    @Test
    fun `scheduling failure retains pending events`() {
        val scheduler = ManualScheduler()
        scheduler.failNext = true
        val received = mutableListOf<Uuid>()
        val endpoint = endpoint { event, _ -> received += event.matchId }
        val delivery = delivery(scheduler, endpoint)
        val session = delivery.startSession()

        delivery.onCommitted(session, 1, startFacts())
        delivery.onReleased(session, 1)
        assertTrue(received.isEmpty())
        delivery.onReleased(session, 1)
        scheduler.runNext()

        assertEquals(listOf(game.matchId), received)
    }

    /** 建立單一開始事件監聽端點。 */
    private fun endpoint(listener: (MatchStartedEvent, GameEventContext) -> Unit): MahjongCraftEvent<MatchStartedListener> = MahjongCraftEvent.create<MatchStartedListener>().also {
        it.register(MatchStartedListener(listener))
    }

    /** 建立使用指定測試端點的事件交付器。 */
    private fun delivery(
        scheduler: ManualScheduler,
        endpoint: MahjongCraftEvent<MatchStartedListener>,
        locations: GameEventLocationSource = GameEventLocationSource {
            TableLocation(dimensionId = "minecraft:overworld", x = 10, y = 64, z = 10)
        },
        reporter: RecordingReporter = RecordingReporter(),
    ): GameEventDelivery = GameEventDelivery(
        projector = GameEventProjector(registry),
        scheduler = scheduler,
        locations = locations,
        reporter = reporter,
        matchStarted = endpoint,
        roundSettled = MahjongCraftEvent.create(),
        matchEnded = MahjongCraftEvent.create(),
    )

    /** 建立一筆對局開始事實。 */
    private fun startFacts(): CommittedGameFacts = CommittedGameFacts(
        venueId = game.id,
        previousGame = null,
        game = game,
        facts = listOf(HistoryEventDraft(null, HistoryFact.MatchStarted(game.tableState, GameFlowConfig(), emptyMap()))),
    )

    /** 建立測試用四人日麻對局。 */
    private fun game(): Game {
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { wind ->
            FakeMahjongPlayerFactory.create(initialSeat = wind).copy(score = 25_000)
        }
        return Game(
            tableState = FakeTableStateFactory.create(
                id = Uuid.random(),
                players = players,
                config = RiichiRuleConfig(),
            ),
            flowConfig = GameFlowConfig(),
            matchId = Uuid.random(),
        )
    }

    private class ManualScheduler : GameEventScheduler {
        /** 尚未執行的工作。 */
        val tasks = ArrayDeque<() -> Unit>()

        /** 下一次排程是否故意失敗。 */
        var failNext: Boolean = false

        override fun enqueue(task: () -> Unit) {
            if (failNext) {
                failNext = false
                error("test scheduler failure")
            }
            tasks += task
        }

        /** 執行一個已排程工作。 */
        fun runNext() {
            check(tasks.isNotEmpty())
            tasks.removeFirst().invoke()
        }
    }

    private class RecordingReporter : GameEventDeliveryReporter {
        /** 找不到位置的事件。 */
        val missingLocations = mutableListOf<Pair<Uuid, Uuid>>()

        /** 監聽者失敗次數。 */
        var listenerFailures: Int = 0

        override fun missingLocation(matchId: Uuid, venueId: Uuid) {
            missingLocations += matchId to venueId
        }

        override fun listenerFailed(event: MatchEvent, cause: Throwable) {
            listenerFailures++
        }

        override fun schedulingFailed(cause: Throwable) = Unit
    }

    private companion object {
        /** 測試規則模組 ID。 */
        const val RULE_MODULE_ID = "mahjongcraft:riichi"
    }
}
