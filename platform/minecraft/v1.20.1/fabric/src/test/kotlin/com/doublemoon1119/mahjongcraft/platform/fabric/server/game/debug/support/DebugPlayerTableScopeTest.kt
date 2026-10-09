package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.GameEventExclusions
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 debug 指令共用牌桌範圍對場次紀錄的排除。 */
class DebugPlayerTableScopeTest {
    /** 用過 debug 指令的場次停止歷史紀錄，之後的事實不再追加。 */
    @Test
    fun `excluding the current match stops its history recording`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val tableState = FakeTableStateFactory.create()
        val game = Game(tableState, GameFlowConfig())
        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games + (game.id to game)),
                result = Unit,
                historyDraftsByVenueId = mapOf(
                    game.id to listOf(HistoryEventDraft(null, HistoryFact.MatchStarted(tableState, game.flowConfig, emptyMap()))),
                ),
            )
        }
        val playerTableScope = DebugPlayerTableScope(
            membershipRepository = PlayerMembershipRepositoryImpl(),
            gameRepository = GameRepositoryImpl(store),
            exclusions = GameEventExclusions(),
            stateStore = store,
            scope = createTestAppCoroutineScope(TestCoroutineDispatchers()),
            dispatchers = TestCoroutineDispatchers(),
        )

        playerTableScope.excludeCurrentMatch(game.id)

        val recording = store.snapshot().historyRecordingState
        assertEquals(HistoryRecordingDecision.STOPPED_EXTERNALLY_MODIFIED, recording.decisionsByMatchId[game.matchId])
        assertEquals(listOf(1L), recording.pendingEvents.map { it.sequence })
        assertEquals(2L, recording.firstMissingSequenceByMatchId[game.matchId])
    }
}
