package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingTerminal
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTransferResult
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證隔離歷史來源轉移的資格、批次邊界、容量與中止語意。 */
class AuthoritativeHistoryTransferTest {
    /** 開始轉移只建立歷史保護狀態，不把來源 Game 加入目前可操作的遊戲集合。 */
    @Test
    fun `history transfer does not mutate live game state`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val game = game()

        assertTrue(store.beginHistoryTransfer(game))

        val state = store.snapshot()
        assertTrue(state.games.isEmpty())
        assertEquals(game.id, state.historyRecordingState.transfersByMatchId.getValue(game.matchId).venueId)
        assertEquals(HistoryRecordingDecision.RECORDING, state.historyRecordingState.decisionsByMatchId[game.matchId])
    }

    /** 最近完整批次可重試且不重複追加，內容不同的重試則遭拒。 */
    @Test
    fun `history transfer accepts exact retry and rejects conflicting retry`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val game = game()
        assertTrue(store.beginHistoryTransfer(game))
        val batch = events(game, 1, HistoryFact.MatchStarted(game.tableState, game.flowConfig))

        assertEquals(HistoryTransferResult.ACCEPTED, store.appendHistoryTransfer(game.matchId, batch))
        assertEquals(HistoryTransferResult.ACCEPTED, store.appendHistoryTransfer(game.matchId, batch))
        assertFailsWith<IllegalArgumentException> {
            store.appendHistoryTransfer(game.matchId, events(game, 1, HistoryFact.MatchStarted(game.tableState, game.flowConfig), occurredAt = 2L))
        }
        assertEquals(listOf(batch.single()), store.snapshot().historyRecordingState.pendingEvents)
    }

    /** 非連續序號、錯誤來源場次及跨場地事件不得進入轉移。 */
    @Test
    fun `history transfer rejects jumps and cross identity events`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val game = game()
        val other = game()
        assertTrue(store.beginHistoryTransfer(game))
        val opening = events(game, 1, HistoryFact.MatchStarted(game.tableState, game.flowConfig))
        assertEquals(HistoryTransferResult.ACCEPTED, store.appendHistoryTransfer(game.matchId, opening))

        assertFailsWith<IllegalArgumentException> {
            store.appendHistoryTransfer(game.matchId, events(game, 3, HistoryFact.ReturnedToRoom))
        }
        assertFailsWith<IllegalArgumentException> {
            store.appendHistoryTransfer(game.matchId, events(other, 2, HistoryFact.ReturnedToRoom))
        }
        assertEquals(listOf(opening.single()), store.snapshot().historyRecordingState.pendingEvents)
    }

    /** 轉移批次超過隔離容量時等待排空，不推進序號或建立缺口。 */
    @Test
    fun `history transfer waits for capacity without changing state`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true, maxPendingHistoryEvents = 2)
        val game = game()
        assertTrue(store.beginHistoryTransfer(game))
        val opening = events(game, 1, HistoryFact.MatchStarted(game.tableState, game.flowConfig))
        assertEquals(HistoryTransferResult.ACCEPTED, store.appendHistoryTransfer(game.matchId, opening))
        val before = store.snapshot().historyRecordingState

        assertEquals(HistoryTransferResult.WAITING_FOR_CAPACITY, store.appendHistoryTransfer(game.matchId, events(game, 2, HistoryFact.ReturnedToRoom)))

        assertEquals(before, store.snapshot().historyRecordingState)
    }

    /** 記錄設定或儲存端停止後，轉移不得再接受批次。 */
    @Test
    fun `history transfer stops when policy or storage becomes unavailable`() = runTest {
        val configStopped = AuthoritativeStateStore(historyRecordingEnabled = true)
        val configGame = game()
        assertTrue(configStopped.beginHistoryTransfer(configGame))
        configStopped.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = false))
        assertEquals(HistoryTransferResult.STOPPED, configStopped.appendHistoryTransfer(configGame.matchId, events(configGame, 1, HistoryFact.MatchStarted(configGame.tableState, configGame.flowConfig))))

        val storageStopped = AuthoritativeStateStore(historyRecordingEnabled = true)
        val storageGame = game()
        assertTrue(storageStopped.beginHistoryTransfer(storageGame))
        storageStopped.applyHistoryStorageAvailability(false)
        assertEquals(HistoryTransferResult.STOPPED, storageStopped.appendHistoryTransfer(storageGame.matchId, events(storageGame, 1, HistoryFact.MatchStarted(storageGame.tableState, storageGame.flowConfig))))
        assertEquals(HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE, storageStopped.snapshot().historyRecordingState.decisionsByMatchId[storageGame.matchId])
    }

    /** 完成轉移必須先接收整場完成與返回房間事實。 */
    @Test
    fun `history transfer completion requires match completed and returned`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val game = game()
        assertTrue(store.beginHistoryTransfer(game))
        val terminal = HistoryRecordingTerminal(100L, true, game.id)

        assertFailsWith<IllegalArgumentException> { store.finishHistoryTransfer(game.matchId, terminal) }
        val completeBatch = listOf(
            event(game, 1, HistoryFact.MatchStarted(game.tableState, game.flowConfig)),
            event(game, 2, HistoryFact.MatchCompleted("test:completed", emptyMap())),
            event(game, 3, HistoryFact.ReturnedToRoom),
        )
        assertEquals(HistoryTransferResult.ACCEPTED, store.appendHistoryTransfer(game.matchId, completeBatch))
        store.finishHistoryTransfer(game.matchId, terminal)

        val state = store.snapshot().historyRecordingState
        assertTrue(game.matchId !in state.transfersByMatchId)
        assertEquals(terminal, state.terminalByMatchId[game.matchId])
    }

    /** 載入含未完成轉移的狀態時，必須固定中止原因並寫入未完成終點。 */
    @Test
    fun `loading state interrupts active history transfer`() = runTest {
        val source = AuthoritativeStateStore(historyRecordingEnabled = true)
        val game = game()
        assertTrue(source.beginHistoryTransfer(game))
        val saved = source.snapshot()
        val restored = AuthoritativeStateStore(historyRecordingEnabled = true)

        restored.load(saved)

        val state = restored.snapshot().historyRecordingState
        assertTrue(state.transfersByMatchId.isEmpty())
        assertEquals(HistoryRecordingDecision.STOPPED_TRANSFER_INTERRUPTED, state.decisionsByMatchId[game.matchId])
        assertFalse(state.terminalByMatchId.getValue(game.matchId).completed)
        assertEquals(game.id, state.terminalByMatchId.getValue(game.matchId).venueId)
        assertTrue(restored.isDirty(), "Recovered interruption evidence must be saved.")
    }

    /**
     * 建立未加入權威狀態的隔離來源 Game。
     *
     * @return 供轉移契約測試使用的來源對局。
     */
    private fun game(): Game = Game(FakeTableStateFactory.create(), GameFlowConfig())

    /**
     * 建立單一交易批次的歷史事件。
     *
     * @param game 來源對局。
     * @param sequence 事件序號。
     * @param fact 權威事實。
     * @param occurredAt UTC 毫秒時間戳。
     * @return 含單一完整交易的事件批次。
     */
    private fun events(game: Game, sequence: Long, fact: HistoryFact, occurredAt: Long = 1L): List<HistoryOutboxEvent> = listOf(event(game, sequence, fact, occurredAt))

    /**
     * 建立指定序號與交易邊界的歷史事件。
     *
     * @param game 來源對局。
     * @param sequence 事件序號，同時作為單事件交易的起始序號。
     * @param fact 權威事實。
     * @param occurredAt UTC 毫秒時間戳。
     * @return 具有來源身分的事件。
     */
    private fun event(game: Game, sequence: Long, fact: HistoryFact, occurredAt: Long = 1L): HistoryOutboxEvent = HistoryOutboxEvent(game.matchId, game.id, game.tableState.roundNumber, sequence, sequence, occurredAt, null, fact)
}
