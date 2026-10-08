package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證權威交易把歷史記錄的事件數交給觀察者。 */
class HistoryRecordingObserverTest {
    /** 每次帶有歷史事件的交易各通知一次新加入待寫佇列的事件數；沒有歷史事件的交易不通知。 */
    @Test
    fun `observer receives appended events per transaction`() = runTest {
        val appended = mutableListOf<Int>()
        val store = AuthoritativeStateStore(historyRecordingEnabled = true, historyRecordingObserver = { _, events -> appended += events })
        val game = store.openGame(FakeTableStateFactory.create())

        val changed = game.copy(automaticControlRevision = 1L)
        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games + (game.id to changed)),
                result = Unit,
                historyDraftsByVenueId = mapOf(game.id to listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom))),
            )
        }
        store.update { state -> AuthoritativeStateUpdate(state.copy(games = state.games + (game.id to changed.copy(automaticControlRevision = 2L))), Unit) }

        assertEquals(listOf(1, 1), appended)
        assertEquals(2, store.snapshot().historyRecordingState.pendingEvents.size)
    }

    /** 不記錄歷史時，帶有歷史事件的交易仍會通知，但沒有事件加入待寫佇列。 */
    @Test
    fun `observer reports zero events while recording is disabled`() = runTest {
        val appended = mutableListOf<Int>()
        val store = AuthoritativeStateStore(historyRecordingEnabled = false, historyRecordingObserver = { _, events -> appended += events })

        store.openGame(FakeTableStateFactory.create())

        assertEquals(listOf(0), appended)
    }
}
