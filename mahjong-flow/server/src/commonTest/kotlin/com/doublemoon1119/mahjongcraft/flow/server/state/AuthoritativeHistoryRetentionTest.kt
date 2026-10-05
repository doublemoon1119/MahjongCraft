package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryPruningConfirmation
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證歷史 metadata 清理與 tombstone 證明的範圍。 */
class AuthoritativeHistoryRetentionTest {
    /** 確認移除有歷史證據的對局會保存終點，載入狀態不會自行推導移除事件。 */
    @Test
    fun `game removal records terminal without load inference`() = runTest {
        val table = FakeTableStateFactory.create()
        val game = Game(table, GameFlowConfig())
        val store = AuthoritativeStateStore()
        store.load(
            AuthoritativeStateSnapshot(
                games = mapOf(table.id to game),
                historyRecordingState = HistoryRecordingState(nextSequenceByMatchId = mapOf(game.matchId to 2L)),
            ),
        )
        store.update { state -> AuthoritativeStateUpdate(state.copy(games = emptyMap()), Unit) }
        val terminal = store.snapshot().historyRecordingState.terminalByMatchId[game.matchId]
        assertEquals(table.id, terminal?.tableId)
        val loaded = AuthoritativeStateStore()
        loaded.load(AuthoritativeStateSnapshot())
        assertTrue(loaded.snapshot().historyRecordingState.terminalByMatchId.isEmpty())
    }

    /** 確認同桌替換場次時，舊場次終點仍被保存。 */
    @Test
    fun `same table match replacement records old terminal`() = runTest {
        val table = FakeTableStateFactory.create()
        val oldGame = Game(table, GameFlowConfig())
        val newGame = oldGame.copy(matchId = Uuid.random())
        val store = AuthoritativeStateStore()
        store.load(
            AuthoritativeStateSnapshot(
                games = mapOf(table.id to oldGame),
                historyRecordingState = HistoryRecordingState(nextSequenceByMatchId = mapOf(oldGame.matchId to 2L)),
            ),
        )
        store.update { state -> AuthoritativeStateUpdate(state.copy(games = mapOf(table.id to newGame)), Unit) }
        assertEquals(table.id, store.snapshot().historyRecordingState.terminalByMatchId[oldGame.matchId]?.tableId)
    }

    /** 確認活動中的 tombstone 證明保留對局與待寫事件，只停止後續追加。 */
    @Test
    fun `active pruning proof retains game and pending event`() = runTest {
        val table = FakeTableStateFactory.create()
        val game = Game(table, GameFlowConfig())
        val event = HistoryOutboxEvent(
            game.matchId,
            game.id,
            1,
            1L,
            1L,
            1L,
            null,
            HistoryFact.ReturnedToRoom,
        )
        val store = AuthoritativeStateStore()
        store.load(
            AuthoritativeStateSnapshot(
                games = mapOf(game.id to game),
                historyRecordingState = HistoryRecordingState(pendingEvents = listOf(event)),
            ),
        )
        store.acknowledgePrunedHistory(listOf(HistoryPruningConfirmation(game.matchId, 2L, "tombstone")))
        val state = store.snapshot()
        assertEquals(game, state.games[game.id])
        assertEquals(listOf(event), state.historyRecordingState.pendingEvents)
        assertEquals(HistoryRecordingDecision.STOPPED_PRUNED, state.historyRecordingState.decisionsByMatchId[game.matchId])
    }

    /** 確認非活動 tombstone 只移除收據涵蓋的待寫事件。 */
    @Test
    fun `inactive pruning proof preserves unrelated pending event`() = runTest {
        val first = Uuid.random()
        val second = Uuid.random()
        val table = Uuid.random()
        fun event(match: Uuid) = HistoryOutboxEvent(
            match,
            table,
            1,
            1L,
            1L,
            1L,
            null,
            HistoryFact.ReturnedToRoom,
        )
        val store = AuthoritativeStateStore()
        store.load(
            AuthoritativeStateSnapshot(
                historyRecordingState = HistoryRecordingState(
                    pendingEvents = listOf(event(first), event(second)),
                ),
            ),
        )
        store.acknowledgePrunedHistory(listOf(HistoryPruningConfirmation(first, 2L, "tombstone")))
        assertEquals(listOf(second), store.snapshot().historyRecordingState.pendingEvents.map { it.matchId })
    }

    /** 確認 metadata acknowledgement 不會清理活動場次或待寫事件。 */
    @Test
    fun `metadata acknowledgement protects active and pending matches`() = runTest {
        val active = Uuid.random()
        val terminal = Uuid.random()
        val store = AuthoritativeStateStore()
        val table = FakeTableStateFactory.create()
        val activeGame = Game(table, GameFlowConfig(), matchId = active)
        store.load(
            AuthoritativeStateSnapshot(
                historyRecordingState = HistoryRecordingState(
                    nextSequenceByMatchId = mapOf(active to 2L, terminal to 3L),
                    firstMissingSequenceByMatchId = mapOf(terminal to 2L),
                    decisionsByMatchId = mapOf(active to HistoryRecordingDecision.RECORDING),
                    terminalByMatchId = mapOf(terminal to HistoryRecordingTerminal(100L, true, Uuid.random())),
                ),
                games = mapOf(table.id to activeGame),
            ),
        )

        store.acknowledgeHistoryMetadata(setOf(active, terminal))

        val state = store.snapshot().historyRecordingState
        assertEquals(2L, state.nextSequenceByMatchId[active])
        assertTrue(terminal !in state.nextSequenceByMatchId)
        assertTrue(terminal !in state.terminalByMatchId)
    }

    /** 確認清理證明只影響指定的非活動場次。 */
    @Test
    fun `pruning confirmation removes only confirmed inactive metadata`() = runTest {
        val first = Uuid.random()
        val second = Uuid.random()
        val store = AuthoritativeStateStore()
        store.load(
            AuthoritativeStateSnapshot(
                historyRecordingState = HistoryRecordingState(
                    nextSequenceByMatchId = mapOf(first to 2L, second to 4L),
                    terminalByMatchId = mapOf(
                        first to HistoryRecordingTerminal(100L, true, Uuid.random()),
                        second to HistoryRecordingTerminal(200L, false, Uuid.random()),
                    ),
                ),
            ),
        )

        store.acknowledgePrunedHistory(listOf(HistoryPruningConfirmation(first, 300L, "archived")))

        val state = store.snapshot().historyRecordingState
        assertTrue(first !in state.nextSequenceByMatchId)
        assertTrue(first !in state.terminalByMatchId)
        assertEquals(4L, state.nextSequenceByMatchId[second])
        assertTrue(second in state.terminalByMatchId)
    }

    /** 確認清理證明拒絕無效的時間戳與原因。 */
    @Test
    fun `pruning confirmation validates proof fields`() {
        assertFailsWith<IllegalArgumentException> {
            HistoryPruningConfirmation(Uuid.random(), -1L, "archived")
        }
        assertFailsWith<IllegalArgumentException> {
            HistoryPruningConfirmation(Uuid.random(), 1L, " ")
        }
    }

    /** 確認儲存端暫停時既有場次留下缺口，恢復後不會重新開始追加。 */
    @Test
    fun `storage pause stops current match without resuming it`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val game = store.openGame(FakeTableStateFactory.create())

        store.applyHistoryStorageAvailability(false)
        val changed = game.copy(remainingReserveMillisByPlayerId = game.remainingReserveMillisByPlayerId.mapValues { 1_000L })
        store.update { state ->
            AuthoritativeStateUpdate(
                state.copy(games = state.games + (game.id to changed)),
                Unit,
                historyDraftsByTableId = mapOf(game.id to listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom))),
            )
        }
        store.applyHistoryStorageAvailability(true)
        val before = store.snapshot().historyRecordingState
        store.update { state ->
            AuthoritativeStateUpdate(
                state.copy(games = state.games + (game.id to changed.copy(automaticControlRevision = 1L))),
                Unit,
                historyDraftsByTableId = mapOf(game.id to listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom))),
            )
        }
        val after = store.snapshot().historyRecordingState
        assertEquals(listOf(1L), before.pendingEvents.map { it.sequence })
        assertEquals(listOf(1L), after.pendingEvents.map { it.sequence })
        assertEquals(HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE, after.decisionsByMatchId[game.matchId])
    }
}
