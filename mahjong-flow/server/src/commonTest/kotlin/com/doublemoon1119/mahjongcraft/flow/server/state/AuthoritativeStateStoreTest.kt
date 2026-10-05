package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableChange
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 Room 與 Game 共用狀態儲存的交易與 dirty tracking。 */
class AuthoritativeStateStoreTest {
    /** 確認舊快照只移除已提交的鍵，不會刪掉其後新增事件或清除缺口。 */
    @Test
    fun `history acknowledgement preserves later events and gaps`() = runTest {
        val store = AuthoritativeStateStore()
        val matchId = Uuid.random()
        val tableId = Uuid.random()
        val first = HistoryOutboxEvent(matchId, tableId, 1, 1, 1, 100L, null, HistoryFact.ReturnedToRoom)
        val second = first.copy(sequence = 2)
        store.load(
            AuthoritativeStateSnapshot(
                historyRecordingState = HistoryRecordingState(
                    nextSequenceByMatchId = mapOf(matchId to 4L),
                    pendingEvents = listOf(first),
                    firstMissingSequenceByMatchId = mapOf(matchId to 3L),
                ),
            ),
        )
        val batchIds = setOf(matchId to 1L)
        store.update { state ->
            AuthoritativeStateUpdate(
                state.copy(historyRecordingState = state.historyRecordingState.copy(pendingEvents = listOf(first, second))),
                Unit,
            )
        }

        assertEquals(1, store.acknowledgeHistoryEvents(batchIds))
        assertEquals(0, store.acknowledgeHistoryEvents(batchIds))
        val recording = store.snapshot().historyRecordingState
        assertEquals(listOf(second), recording.pendingEvents)
        assertEquals(4L, recording.nextSequenceByMatchId[matchId])
        assertEquals(3L, recording.firstMissingSequenceByMatchId[matchId])
    }

    /** 同筆交易有多個語意事實時，只在最後保存一次可還原的桌況差異。 */
    @Test
    fun `multiple facts in one transaction share one table change`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val before = FakeTableStateFactory.create()
        val after = before.copy(
            players = before.players.mapIndexed { index, player ->
                if (index == 0) player.copy(score = player.score + 100) else player
            },
            tileWall = before.tileWall.draw().wall,
            currentPlayerIndex = 1,
        )
        val game = store.openGame(before)

        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games + (game.id to game.copy(tableState = after))),
                result = Unit,
                historyDraftsByTableId = mapOf(
                    game.id to listOf(
                        HistoryEventDraft(null, HistoryFact.ReturnedToRoom),
                        HistoryEventDraft(null, HistoryFact.ReturnedToRoom),
                    ),
                ),
            )
        }

        val events = store.snapshot().historyRecordingState.pendingEvents.filterNot { it.fact is HistoryFact.MatchStarted }
        assertEquals(listOf(2L, 3L, 4L), events.map { it.sequence })
        assertEquals(listOf(2L, 2L, 2L), events.map { it.transactionFirstSequence })
        val result = assertIs<HistoryTableResult.Change>(assertIs<HistoryFact.TableChanged>(events.last().fact).result)
        assertEquals(after, result.change.applyTo(before))
        assertEquals(1, events.count { it.fact is HistoryFact.TableChanged })
    }

    /** 未支援的桌況欄位變更必須留下標示原因的檢查點。 */
    @Test
    fun `unsupported table change uses one explicit checkpoint`() {
        val before = FakeTableStateFactory.create()
        val after = before.copy(comboCount = 1)

        assertNull(HistoryTableChange.between(before, after))
    }

    /** 玩家動作紀錄只保存新增部分，仍可還原完整累積紀錄。 */
    @Test
    fun `player history change appends actions without repeating the prefix`() {
        val before = FakeTableStateFactory.create().let { state ->
            state.copy(
                players = state.players.mapIndexed { index, player ->
                    if (index == 0) player.copy(actionHistory = listOf(GameAction.Draw)) else player
                },
            )
        }
        val after = before.copy(
            players = before.players.mapIndexed { index, player ->
                if (index == 0) player.copy(actionHistory = player.actionHistory + GameAction.Draw) else player
            },
        )

        val change = checkNotNull(HistoryTableChange.between(before, after))
        assertEquals(1, change.changedPlayers.single().retainedActionCount)
        assertEquals(listOf(GameAction.Draw), change.changedPlayers.single().appendedActions)
        assertEquals(after, change.applyTo(before))
    }

    /** 驗證兩個 repository 透過同一個 store 完成基本新增、讀取與刪除。 */
    @Test
    fun `room and game repositories share one store`() = runTest {
        val store = AuthoritativeStateStore()
        val roomRepository = RoomRepositoryImpl(store)
        val gameRepository = GameRepositoryImpl(store)
        val room = createRoom()
        val game = FakeTableStateFactory.create()

        roomRepository.setRoom(room)
        gameRepository.setTableState(game)

        assertEquals(room, roomRepository.getRoom(room.id))
        assertEquals(game, gameRepository.getTableState(game.id))
        assertEquals(setOf(room.id), store.snapshot().rooms.keys)
        assertEquals(setOf(game.id), store.snapshot().games.keys)

        roomRepository.removeRoom(room.id)
        gameRepository.removeTableState(game.id)

        assertNull(roomRepository.getRoom(room.id))
        assertNull(gameRepository.getTableState(game.id))
    }

    /** 驗證純讀取與無實際變更的交易不會標記 dirty 或通知 listener。 */
    @Test
    fun `reads and unchanged updates remain clean`() = runTest {
        val store = AuthoritativeStateStore()
        var notifications = 0
        store.setDirtyListener { notifications++ }

        store.getRoom(Uuid.random())
        store.snapshot()
        store.update { state -> AuthoritativeStateUpdate(state, Unit) }

        assertFalse(store.isDirty())
        assertEquals(0, notifications)
    }

    /** 驗證每次實際變更都標記 dirty 並通知平台 listener。 */
    @Test
    fun `mutations mark dirty and notify listener`() = runTest {
        val store = AuthoritativeStateStore()
        val repository = RoomRepositoryImpl(store)
        var notifications = 0
        store.setDirtyListener { notifications++ }

        repository.setRoom(createRoom())

        assertTrue(store.isDirty())
        assertEquals(1, notifications)

        store.markClean()

        assertFalse(store.isDirty())
    }

    /** 驗證載入存檔會整批替換狀態且維持 clean。 */
    @Test
    fun `load replaces all state without marking dirty`() = runTest {
        val store = AuthoritativeStateStore()
        val oldRoom = createRoom()
        RoomRepositoryImpl(store).setRoom(oldRoom)
        val loadedRoom = createRoom()
        val loadedGame = FakeTableStateFactory.create()

        store.load(
            AuthoritativeStateSnapshot(
                rooms = mapOf(loadedRoom.id to loadedRoom),
                games = mapOf(loadedGame.id to Game(loadedGame, GameFlowConfig())),
            ),
        )

        assertFalse(store.isDirty())
        assertNull(store.getRoom(oldRoom.id))
        assertEquals(loadedRoom, store.getRoom(loadedRoom.id))
        assertEquals(loadedGame, store.getGame(loadedGame.id)?.tableState)
    }

    /** 驗證 Room → Game 能在單次交易中完成且只通知一次。 */
    @Test
    fun `room to game transition commits atomically`() = runTest {
        val store = AuthoritativeStateStore()
        val room = createRoom()
        store.load(AuthoritativeStateSnapshot(rooms = mapOf(room.id to room)))
        val tableState = FakeTableStateFactory.create(id = room.id)
        val game = Game(tableState, GameFlowConfig())
        var notifications = 0
        store.setDirtyListener { notifications++ }

        store.update { state ->
            AuthoritativeStateUpdate(
                state.copy(
                    rooms = state.rooms - room.id,
                    games = state.games + (game.id to game),
                ),
                Unit,
            )
        }

        val snapshot = store.snapshot()
        assertTrue(snapshot.rooms.isEmpty())
        assertEquals(game, snapshot.games[room.id])
        assertEquals(1, notifications)
    }

    /** 驗證相同桌子 ID 不可透過不同 repository 同時保存為 Room 與 Game。 */
    @Test
    fun `same table ID cannot be stored as room and game`() = runTest {
        val store = AuthoritativeStateStore()
        val room = createRoom()
        RoomRepositoryImpl(store).setRoom(room)

        assertFailsWith<IllegalArgumentException> {
            GameRepositoryImpl(store).setTableState(FakeTableStateFactory.create(id = room.id))
        }
    }

    /** 驗證 repository 可原子更新完整 Game 的流程 runtime 狀態。 */
    @Test
    fun `game repository updates remaining reserve time atomically`() = runTest {
        val store = AuthoritativeStateStore()
        val repository = GameRepositoryImpl(store)
        val playerId = Uuid.random()
        val tableState = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
        )
        repository.setTableState(tableState)

        repository.updateGame(tableState.id) { game ->
            game?.copy(remainingReserveMillisByPlayerId = mapOf(playerId to 12_345L)) to Unit
        }

        assertEquals(12_345L, repository.getGame(tableState.id)?.remainingReserveMillisByPlayerId?.get(playerId))
    }

    /** 驗證歷史 outbox 與遊戲狀態在同一筆 store 交易中一起提交。 */
    @Test
    fun `history outbox commits atomically with game state`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val game = store.openGame(FakeTableStateFactory.create())

        store.update { state ->
            val nextGame = game.copy(
                remainingReserveMillisByPlayerId = game.tableState.players.associate { player ->
                    player.id to 1_000L
                },
            )
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games + (game.id to nextGame)),
                result = Unit,
                historyDraftsByTableId = mapOf(
                    game.id to listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom)),
                ),
            )
        }

        val snapshot = store.snapshot()
        assertEquals(
            setOf(1_000L),
            snapshot.games.getValue(game.id).remainingReserveMillisByPlayerId.values.toSet(),
        )
        val event = snapshot.historyRecordingState.pendingEvents.last()
        assertEquals(2L, event.sequence)
        assertEquals(game.matchId, event.matchId)
        assertEquals(HistoryFact.ReturnedToRoom, event.fact)
    }

    /** 驗證預設關閉時不會在權威狀態中累積歷史 outbox。 */
    @Test
    fun `history recording is disabled by default`() = runTest {
        val store = AuthoritativeStateStore()
        val tableState = FakeTableStateFactory.create()
        GameRepositoryImpl(store).setTableState(tableState)
        val game = store.getGame(tableState.id)!!

        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(
                    games = state.games + (
                        game.id to game.copy(
                            remainingReserveMillisByPlayerId = game.tableState.players.associate { player ->
                                player.id to 1_000L
                            },
                        )
                        ),
                ),
                result = Unit,
                historyDraftsByTableId = mapOf(
                    game.id to listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom)),
                ),
            )
        }

        assertTrue(store.snapshot().historyRecordingState.pendingEvents.isEmpty())
    }

    /** 驗證事件容量耗盡時保留穩定序號缺口，而不阻塞狀態交易。 */
    @Test
    fun `history recording records sequence gap when pending capacity is exhausted`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true, maxPendingHistoryEvents = 2)
        val game = store.openGame(FakeTableStateFactory.create())
        val drafts = listOf(
            HistoryEventDraft(null, HistoryFact.ReturnedToRoom),
            HistoryEventDraft(null, HistoryFact.ReturnedToRoom),
        )

        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(
                    games = state.games + (
                        game.id to game.copy(
                            remainingReserveMillisByPlayerId = game.tableState.players.associate { player ->
                                player.id to 1_000L
                            },
                        )
                        ),
                ),
                result = Unit,
                historyDraftsByTableId = mapOf(game.id to drafts),
            )
        }

        val recording = store.snapshot().historyRecordingState
        assertEquals(listOf(1L, 2L), recording.pendingEvents.map { it.sequence })
        assertEquals(4L, recording.nextSequenceByMatchId.getValue(game.matchId))
        assertEquals(3L, recording.firstMissingSequenceByMatchId.getValue(game.matchId))
    }

    /** 歷史記錄函式失敗不能撤銷已接受的權威變更，且須留下可查的缺口。 */
    @Test
    fun `history extractor failure does not block game update`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val game = store.openGame(FakeTableStateFactory.create())

        repository.updateGame(
            gameId = game.id,
            history = { _, _, _ -> error("history recording failed") },
        ) { current ->
            current!!.copy(
                remainingReserveMillisByPlayerId = current.tableState.players.associate { it.id to 1_000L },
            ) to Unit
        }

        val snapshot = store.snapshot()
        assertEquals(setOf(1_000L), snapshot.games.getValue(game.id).remainingReserveMillisByPlayerId.values.toSet())
        assertEquals(listOf(1L), snapshot.historyRecordingState.pendingEvents.map { it.sequence })
        assertEquals(2L, snapshot.historyRecordingState.firstMissingSequenceByMatchId.getValue(game.matchId))
        assertEquals(3L, snapshot.historyRecordingState.nextSequenceByMatchId.getValue(game.matchId))
    }

    /** 同桌重開須用新的場次身分；同筆交易內的事件依提交順序取得序號。 */
    @Test
    fun `history sequences are scoped to match rather than table`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val tableState = FakeTableStateFactory.create()
        val firstGame = store.openGame(tableState)

        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games + (firstGame.id to firstGame.copy(isMatchOver = true))),
                result = Unit,
                historyDraftsByTableId = mapOf(
                    firstGame.id to listOf(
                        HistoryEventDraft(null, HistoryFact.ReturnedToRoom),
                        HistoryEventDraft(null, HistoryFact.ReturnedToRoom),
                    ),
                ),
            )
        }
        val secondGame = firstGame.copy(matchId = Uuid.random())
        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games + (secondGame.id to secondGame)),
                result = Unit,
                historyDraftsByTableId = mapOf(
                    secondGame.id to listOf(
                        HistoryEventDraft(null, HistoryFact.MatchStarted(secondGame.tableState, secondGame.flowConfig)),
                        HistoryEventDraft(null, HistoryFact.ReturnedToRoom),
                    ),
                ),
            )
        }

        val events = store.snapshot().historyRecordingState.pendingEvents
        assertEquals(listOf(1L, 2L, 3L), events.filter { it.matchId == firstGame.matchId }.map { it.sequence })
        assertEquals(listOf(1L, 2L), events.filter { it.matchId == secondGame.matchId }.map { it.sequence })
        assertEquals(setOf(tableState.id), events.map { it.tableId }.toSet())
    }

    /** 倉庫拒絕或無變更的交易不得產生歷史事件。 */
    @Test
    fun `unchanged game update does not invoke history recording`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val tableState = FakeTableStateFactory.create()
        repository.setTableState(tableState)

        repository.updateGame(
            gameId = tableState.id,
            history = { _, _, _ -> error("History extractor must not run for unchanged state") },
        ) { game -> game to Unit }

        assertTrue(store.snapshot().historyRecordingState.pendingEvents.isEmpty())
        assertTrue(store.snapshot().historyRecordingState.firstMissingSequenceByMatchId.isEmpty())
    }

    /** 關閉歷史記錄時仍會建立並送出已提交事實，但不寫入歷史待寫佇列。 */
    @Test
    fun `committed facts are published even when history recording is disabled`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = false)
        val repository = GameRepositoryImpl(store)
        val tableState = FakeTableStateFactory.create()
        repository.setTableState(tableState)
        val before = store.getGame(tableState.id)!!
        val received = mutableListOf<CommittedGameFacts>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.committedFacts.toList(received) }

        repository.updateGame(
            gameId = before.id,
            history = { _, _, _ -> listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom)) },
        ) { current -> current!!.copy(isMatchOver = true) to Unit }
        runCurrent()

        val facts = received.single()
        assertEquals(before.id, facts.tableId)
        assertEquals(before, facts.previousGame)
        assertEquals(store.getGame(before.id), facts.game)
        assertEquals(listOf(HistoryFact.ReturnedToRoom), facts.facts.map { it.fact })
        assertTrue(store.snapshot().historyRecordingState.pendingEvents.isEmpty())
    }

    /** 失敗的交易不會提交，也不會送出事實。 */
    @Test
    fun `failed transactions publish no committed facts`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val tableState = FakeTableStateFactory.create()
        repository.setTableState(tableState)
        val received = mutableListOf<CommittedGameFacts>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.committedFacts.toList(received) }

        assertFailsWith<IllegalStateException> {
            repository.updateGame(
                gameId = tableState.id,
                history = { _, _, _ -> listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom)) },
            ) { _ -> error("rejected") }
        }
        runCurrent()

        assertTrue(received.isEmpty())
    }

    /** 沒有改變對局的交易不會送出事實。 */
    @Test
    fun `unchanged game updates publish no committed facts`() = runTest {
        val store = AuthoritativeStateStore()
        val repository = GameRepositoryImpl(store)
        val tableState = FakeTableStateFactory.create()
        repository.setTableState(tableState)
        val received = mutableListOf<CommittedGameFacts>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.committedFacts.toList(received) }

        repository.updateGame(
            gameId = tableState.id,
            history = { _, _, _ -> listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom)) },
        ) { game -> game to Unit }
        runCurrent()

        assertTrue(received.isEmpty())
    }

    /** 對局中途出現、沒有開局事實的新場次不從中途建立歷史。 */
    @Test
    fun `new match without an opening fact is not recorded`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val game = store.openGame(FakeTableStateFactory.create())
        val replaced = game.copy(matchId = Uuid.random(), automaticControlRevision = 1L)

        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games + (replaced.id to replaced)),
                result = Unit,
                historyDraftsByTableId = mapOf(replaced.id to listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom))),
            )
        }

        val recording = store.snapshot().historyRecordingState
        assertEquals(HistoryRecordingDecision.EXCLUDED_NO_OPENING, recording.decisionsByMatchId[replaced.matchId])
        assertTrue(recording.pendingEvents.none { it.matchId == replaced.matchId })
    }

    /** 開局事實與新場次同筆提交時照記錄政策記錄，開局事實為序號 1。 */
    @Test
    fun `new match with an opening fact is recorded from sequence one`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)

        val game = store.openGame(FakeTableStateFactory.create())

        val recording = store.snapshot().historyRecordingState
        assertEquals(HistoryRecordingDecision.RECORDING, recording.decisionsByMatchId[game.matchId])
        val opening = recording.pendingEvents.single()
        assertEquals(1L, opening.sequence)
        assertIs<HistoryFact.MatchStarted>(opening.fact)
    }

    /** 停止記錄後保留缺口，之後的交易不再追加事件。 */
    @Test
    fun `stopping history recording leaves a gap and ignores later facts`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val game = store.openGame(FakeTableStateFactory.create())

        store.stopHistoryRecording(game.matchId)
        store.update { state ->
            AuthoritativeStateUpdate(
                state = state.copy(games = state.games + (game.id to game.copy(automaticControlRevision = 1L))),
                result = Unit,
                historyDraftsByTableId = mapOf(game.id to listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom))),
            )
        }

        val recording = store.snapshot().historyRecordingState
        assertEquals(HistoryRecordingDecision.STOPPED_EXTERNALLY_MODIFIED, recording.decisionsByMatchId[game.matchId])
        assertEquals(listOf(1L), recording.pendingEvents.map { it.sequence })
        assertEquals(2L, recording.firstMissingSequenceByMatchId[game.matchId])
    }

    /** 沒在記錄的場次不受停止記錄影響。 */
    @Test
    fun `stopping history recording keeps an excluded decision`() = runTest {
        val store = AuthoritativeStateStore()
        val game = store.openGame(FakeTableStateFactory.create())

        store.stopHistoryRecording(game.matchId)

        val recording = store.snapshot().historyRecordingState
        assertEquals(HistoryRecordingDecision.EXCLUDED_CONFIG_DISABLED, recording.decisionsByMatchId[game.matchId])
        assertNull(recording.firstMissingSequenceByMatchId[game.matchId])
    }

    /** 建立最小等待階段 Room。 */
    private fun createRoom(): Room {
        val hostId = Uuid.random()
        return Room(
            id = Uuid.random(),
            hostId = hostId,
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            playerIds = listOf(hostId),
        )
    }
}
