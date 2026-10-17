package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortEffects
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.flow.server.state.CommittedFactsListener
import com.doublemoon1119.mahjongcraft.flow.server.time.MonotonicClockImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證對局中止的原子移除、回房通知、歷史事實與過期保護。 */
class AbortGameUseCaseTest {
    /** 中止未完成對局後，保留房間資料並只寫入一筆提前終止事實。 */
    @Test
    fun `abort atomically restores room and records one aborted fact`() = runTest {
        val fixtures = Fixtures(historyRecordingEnabled = true)
        val game = fixtures.openGame()
        fixtures.seedSnapshots(game)

        assertTrue(fixtures.useCase.abort(game, "test:broken", "test diagnostic"))

        val room = fixtures.store.getRoom(game.id)
        assertNotNull(room)
        assertNull(fixtures.store.getGame(game.id))
        assertEquals(game.roomPlayerIds, room.playerIds)
        assertEquals(game.aiPlayerStrategyKeys, room.aiPlayerStrategyKeys)
        assertEquals(game.flowConfig, room.gameConfig.flowConfig)
        assertEquals(game.tableState.config, room.gameConfig.ruleConfig)
        assertEquals(JoinReason.MatchAborted, fixtures.roomEvents.getJoinReason(game.id, game.roomPlayerIds.first(), game.roomPlayerIds.first()))
        assertTrue(fixtures.presentation.werePlayerTilesCleared(game.id))
        assertEquals(1, fixtures.abortedFacts(game).size)
        val events = GameEventProjector(fixtures.moduleRegistry).project(fixtures.abortedFacts(game).single())
        assertEquals(1, events.count { it is MatchEndedEvent })
        assertEquals(
            game.tableState.players.associate { it.id to it.score },
            fixtures.abortedFacts(game).single().previousGame?.tableState?.players?.associate { it.id to it.score },
        )
        assertTrue(events.none { it is RoundSettledEvent })
    }

    /** 外部清理失敗仍移除舊快照並通知回房，且不吞掉原始錯誤。 */
    @Test
    fun `failed external effects still restore core room notifications`() = runTest {
        val fixtures = Fixtures(effects = GameAbortEffects { _, _, _, _, _ -> error("effect failed") })
        val game = fixtures.openGame()
        fixtures.seedSnapshots(game)

        assertFailsWith<IllegalStateException> {
            fixtures.useCase.abort(game, "test:broken", "test diagnostic")
        }

        assertNull(fixtures.store.getGame(game.id))
        assertNotNull(fixtures.store.getRoom(game.id))
        assertNull(fixtures.gameSnapshots.getSnapshot(game.id, game.roomPlayerIds.first()))
        assertEquals(JoinReason.MatchAborted, fixtures.roomEvents.getJoinReason(game.id, game.roomPlayerIds.first(), game.roomPlayerIds.first()))
    }

    /** 中止只清除目標對局的快照，不影響其他對局。 */
    @Test
    fun `abort removes only target game snapshots`() = runTest {
        val fixtures = Fixtures()
        val game = fixtures.openGame()
        val otherGame = fixtures.openGame(tableId = Uuid.random())
        fixtures.seedSnapshots(game)
        fixtures.seedSnapshots(otherGame)

        assertTrue(fixtures.useCase.abort(game, "test:missing", "missing table"))

        assertNull(fixtures.gameSnapshots.getSnapshot(game.id, game.roomPlayerIds.first()))
        assertNotNull(fixtures.gameSnapshots.getSnapshot(otherGame.id, otherGame.roomPlayerIds.first()))
    }

    /** 舊快照不能移除已經改變的對局。 */
    @Test
    fun `stale expected game cannot abort current game`() = runTest {
        val fixtures = Fixtures()
        val game = fixtures.openGame()
        fixtures.gameRepo.updateGame(game.id) { current -> current!!.copy(isMatchOver = false, automaticControlRevision = 1L) to Unit }

        assertFalse(fixtures.useCase.abort(game, "test:stale", "stale"))
        assertNotNull(fixtures.store.getGame(game.id))
        assertNull(fixtures.store.getRoom(game.id))
    }

    /** 舊場次不能移除同一場地上後來建立的新場次。 */
    @Test
    fun `old match cannot abort replacement game`() = runTest {
        val fixtures = Fixtures(historyRecordingEnabled = true)
        val oldGame = fixtures.openGame()
        fixtures.store.update { state ->
            AuthoritativeStateUpdate(state.copy(games = state.games - oldGame.id), Unit)
        }
        val newGame = fixtures.openGame(tableId = oldGame.id, matchId = Uuid.random())

        assertFalse(fixtures.useCase.abort(oldGame, "test:stale", "old match"))
        assertEquals(newGame, fixtures.store.getGame(oldGame.id))
        assertNull(fixtures.store.getRoom(oldGame.id))
    }

    /** 重複中止不會重複移除或再次提交提前終止事實。 */
    @Test
    fun `repeated abort does not create duplicate fact`() = runTest {
        val fixtures = Fixtures(historyRecordingEnabled = true)
        val game = fixtures.openGame()

        assertTrue(fixtures.useCase.abort(game, "test:broken", "first"))
        assertFalse(fixtures.useCase.abort(game, "test:broken", "second"))
        assertEquals(1, fixtures.abortedFacts(game).size)
    }

    /** 已正常結束的對局不會被誤記為中止。 */
    @Test
    fun `completed game is not aborted`() = runTest {
        val fixtures = Fixtures(historyRecordingEnabled = true)
        val game = fixtures.openGame()
        val completed = game.copy(isMatchOver = true)
        fixtures.gameRepo.updateGame(game.id) { completed to Unit }

        assertFalse(fixtures.useCase.abort(completed, "test:broken", "already completed"))
        assertEquals(completed, fixtures.store.getGame(game.id))
        assertTrue(fixtures.abortedFacts(game).isEmpty())
    }

    /**
     * 測試用的中止流程依賴，使用正式權威儲存與遊戲 repository。
     *
     * @param historyRecordingEnabled 是否同時驗證中止後的歷史事實。
     * @param effects 額外清理出口，可模擬外部失敗。
     */
    private class Fixtures(
        historyRecordingEnabled: Boolean = false,
        effects: GameAbortEffects = GameAbortEffects.NONE,
    ) {
        val store = AuthoritativeStateStore(historyRecordingEnabled = historyRecordingEnabled, maxPendingHistoryEvents = 10_000)
        val gameRepo = GameRepositoryImpl(store)
        val gameSnapshots = FakeGameSnapshotRepository()
        private val roomSnapshots = FakeRoomSnapshotRepository()
        val roomEvents = FakeRoomEventPublisher()
        val presentation = FakeGamePresentationPublisher()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val useCase = AbortGameUseCase(
            store = store,
            gameSnapshots = gameSnapshots,
            roomSnapshots = roomSnapshots,
            roomEvents = roomEvents,
            presentation = presentation,
            availability = availabilityService(),
            winHandoff = WinPresentationHandoff(),
            effects = effects,
        )

        suspend fun openGame(
            tableId: Uuid = Uuid.random(),
            matchId: Uuid = Uuid.random(),
        ): Game {
            val table = FakeTableStateFactory.create(id = tableId, config = RiichiRuleConfig())
            val scoredTable = table.copy(
                players = table.players.mapIndexed { index, player ->
                    player.copy(score = 25_000 + index * 1_000)
                },
            )
            val base = Game(tableState = scoredTable, flowConfig = GameFlowConfig(), matchId = matchId)
            val game = base.copy(aiPlayerStrategyKeys = base.tableState.players.take(1).associate { it.id to "test:ai" })
            return store.update { state ->
                AuthoritativeStateUpdate(
                    state = state.copy(games = state.games + (game.id to game)),
                    result = game,
                    historyDraftsByVenueId = mapOf(
                        game.id to listOf(
                            HistoryEventDraft(
                                actorPlayerId = null,
                                fact = HistoryFact.MatchStarted(game.tableState, game.flowConfig, game.aiPlayerStrategyKeys),
                            ),
                        ),
                    ),
                )
            }
        }

        suspend fun seedSnapshots(game: Game) {
            val visibility = GameVisibilityPolicyImpl(moduleRegistry)
            game.roomPlayerIds.forEach { observerId ->
                gameSnapshots.setSnapshot(observerId, visibility.snapshotFor(game, observerId))
            }
        }

        fun abortedFacts(game: Game): List<CommittedGameFacts> = committedFacts
            .filter { it.venueId == game.id && it.facts.any { draft -> draft.fact is HistoryFact.MatchAborted } }

        private val committedFacts = mutableListOf<CommittedGameFacts>()

        init {
            store.setCommittedFactsListener(object : CommittedFactsListener {
                override fun onCommitted(sequence: Long, facts: CommittedGameFacts) {
                    committedFacts += facts
                }

                override fun onReleased(sequence: Long) = Unit
            })
        }

        private fun availabilityService(): GameDecisionAvailabilityService {
            val repository = GameRepositoryImpl(store)
            val clock = MonotonicClockImpl()
            val manager = GameDecisionTimerManager(repository, GameDecisionAuthorityResolver(), PlayerDecisionTimerFactory(clock), clock)
            return GameDecisionAvailabilityService(
                FakeGamePresentationBusyGate(),
                manager,
                DecisionTimerSynchronizationService(manager, repository, FakeDecisionTimerUpdatePublisher()),
            )
        }
    }
}
