package com.doublemoon1119.mahjongcraft.flow.server.state

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證歷史記錄資格在權威狀態交易、載入與政策變更下的固定性。 */
class HistoryRecordingPolicyTest {
    /** 新對局在啟用政策下建立記錄資格並保存開局快照。 */
    @Test
    fun `new game records match started history when policy is enabled`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val table = FakeTableStateFactory.create()

        repository.updateGame(table.id, history = { _, _, _ -> listOf(matchStarted(table)) }) {
            Game(table, GameFlowConfig()) to Unit
        }

        val recording = store.snapshot().historyRecordingState
        val game = checkNotNull(store.getGame(table.id))
        assertEquals(HistoryRecordingDecision.RECORDING, recording.decisionsByMatchId[game.matchId])
        assertTrue(recording.pendingEvents.single().fact is HistoryFact.MatchStarted, "MatchStarted history should be persisted")
    }

    /** 含有 AI 座位的新對局在排除 AI 的政策下不建立歷史事件。 */
    @Test
    fun `new game with any AI is excluded when AI matches are disabled`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        store.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = true, includeAiMatches = false))
        val repository = GameRepositoryImpl(store)
        val table = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(),
                FakeMahjongPlayerFactory.create(initialSeatIndex = 1),
            ),
        )
        val aiPlayerStrategyKeys = mapOf(table.players[1].id to "test:ai")
        repository.updateGame(table.id, history = { _, _, _ -> listOf(matchStarted(table, aiPlayerStrategyKeys)) }) {
            Game(table, GameFlowConfig(), aiPlayerStrategyKeys = aiPlayerStrategyKeys) to Unit
        }

        val recording = store.snapshot().historyRecordingState
        val game = checkNotNull(store.getGame(table.id))
        assertEquals(HistoryRecordingDecision.EXCLUDED_AI, recording.decisionsByMatchId[game.matchId])
        assertTrue(recording.pendingEvents.isEmpty(), "Excluded games must not create history events")
    }

    /** 關閉後重新開啟政策也不會恢復已停止的進行中對局。 */
    @Test
    fun `disabling and re-enabling policy does not resume an active match`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val table = FakeTableStateFactory.create()
        repository.updateGame(table.id, history = { _, _, _ -> listOf(matchStarted(table)) }) {
            Game(table, GameFlowConfig()) to Unit
        }
        val game = checkNotNull(store.getGame(table.id))

        store.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = false))
        store.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = true))

        val afterReenable = table.copy(
            players = table.players.mapIndexed { index, player ->
                if (index == 0) player.copy(score = player.score + 1) else player
            },
        )
        repository.updateGame(afterReenable.id, history = { _, _, _ -> listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom)) }) {
            game.copy(tableState = afterReenable) to Unit
        }

        val recording = store.snapshot().historyRecordingState
        assertEquals(HistoryRecordingDecision.STOPPED_CONFIG_DISABLED, recording.decisionsByMatchId[game.matchId])
        assertEquals(3L, recording.nextSequenceByMatchId[game.matchId])
        assertEquals(2L, recording.firstMissingSequenceByMatchId[game.matchId])
        assertEquals(1, recording.pendingEvents.size)
    }

    /** 改變 AI 篩選設定不會重新分類已開始的對局。 */
    @Test
    fun `changing AI policy does not reclassify an existing match`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val table = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(),
                FakeMahjongPlayerFactory.create(initialSeatIndex = 1),
            ),
        )
        val aiPlayerStrategyKeys = mapOf(table.players[1].id to "test:ai")
        repository.updateGame(table.id, history = { _, _, _ -> listOf(matchStarted(table, aiPlayerStrategyKeys)) }) {
            Game(table, GameFlowConfig(), aiPlayerStrategyKeys = aiPlayerStrategyKeys) to Unit
        }
        val game = checkNotNull(store.getGame(table.id))

        store.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = true, includeAiMatches = false))

        assertEquals(HistoryRecordingDecision.RECORDING, store.snapshot().historyRecordingState.decisionsByMatchId[game.matchId])
    }

    /** 同一場地承載新的 match ID 時，新的對局重新依政策判定。 */
    @Test
    fun `new match on the same table is evaluated again`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        store.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = true, includeAiMatches = false))
        val repository = GameRepositoryImpl(store)
        val table = FakeTableStateFactory.create()
        repository.updateGame(table.id, history = { _, _, _ -> listOf(matchStarted(table)) }) {
            Game(table, GameFlowConfig()) to Unit
        }
        val firstGame = checkNotNull(store.getGame(table.id))
        repository.updateGame(table.id) { null to Unit }
        val aiPlayerStrategyKeys = mapOf(table.players[1].id to "test:ai")
        repository.updateGame(table.id, history = { _, _, _ -> listOf(matchStarted(table, aiPlayerStrategyKeys)) }) {
            Game(table, GameFlowConfig(), aiPlayerStrategyKeys = aiPlayerStrategyKeys) to Unit
        }

        val recording = store.snapshot().historyRecordingState
        val secondGame = checkNotNull(store.getGame(table.id))
        assertTrue(firstGame.matchId != secondGame.matchId, "A new game must have a new match ID")
        assertEquals(HistoryRecordingDecision.RECORDING, recording.decisionsByMatchId[firstGame.matchId])
        assertEquals(HistoryRecordingDecision.EXCLUDED_AI, recording.decisionsByMatchId[secondGame.matchId])
    }

    /** 載入的既有資格決策優先於目前政策並保持不變。 */
    @Test
    fun `loading saved decision preserves its status`() = runTest {
        val table = FakeTableStateFactory.create()
        val game = Game(table, GameFlowConfig())
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        store.load(
            AuthoritativeStateSnapshot(
                games = mapOf(game.id to game),
                historyRecordingState = HistoryRecordingState(
                    decisionsByMatchId = mapOf(game.matchId to HistoryRecordingDecision.EXCLUDED_AI),
                ),
            ),
        )
        store.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = true))

        assertEquals(HistoryRecordingDecision.EXCLUDED_AI, store.snapshot().historyRecordingState.decisionsByMatchId[game.matchId])
    }

    /** 缺少開局序號證據的載入對局不會在中途新增歷史。 */
    @Test
    fun `loaded game without opening evidence is not recorded`() = runTest {
        val table = FakeTableStateFactory.create()
        val game = Game(table, GameFlowConfig())
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        store.load(AuthoritativeStateSnapshot(games = mapOf(game.id to game)))
        store.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = true))
        val repository = GameRepositoryImpl(store)

        repository.updateGame(table.id, history = { _, _, _ -> listOf(matchStarted(table)) }) { current ->
            current!!.copy(
                tableState = table.copy(
                    players = table.players.mapIndexed { index, player ->
                        if (index == 0) player.copy(score = player.score + 1) else player
                    },
                ),
            ) to Unit
        }

        val recording = store.snapshot().historyRecordingState
        assertEquals(HistoryRecordingDecision.EXCLUDED_NO_OPENING, recording.decisionsByMatchId[game.matchId])
        assertTrue(recording.pendingEvents.isEmpty(), "A loaded game without opening evidence must not start recording")
    }

    /** 待寫容量耗盡時保留記錄資格並寫入可診斷的序號缺口。 */
    @Test
    fun `history capacity exhaustion leaves a sequence gap`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true, maxPendingHistoryEvents = 0)
        val repository = GameRepositoryImpl(store)
        val table = FakeTableStateFactory.create()
        repository.updateGame(table.id, history = { _, _, _ -> listOf(matchStarted(table)) }) {
            Game(table, GameFlowConfig()) to Unit
        }

        val game = checkNotNull(store.getGame(table.id))
        val recording = store.snapshot().historyRecordingState
        assertEquals(HistoryRecordingDecision.RECORDING, recording.decisionsByMatchId[game.matchId])
        assertEquals(2L, recording.nextSequenceByMatchId[game.matchId])
        assertEquals(1L, recording.firstMissingSequenceByMatchId[game.matchId])
    }

    /** 政策更新與對局交易並發時，最終狀態仍符合單一交易順序。 */
    @Test
    fun `concurrent policy and game updates preserve an atomic decision`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val table = FakeTableStateFactory.create()
        val operations = listOf(
            async {
                store.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = false))
            },
            async {
                repository.updateGame(table.id, history = { _, _, _ -> listOf(matchStarted(table)) }) {
                    Game(table, GameFlowConfig()) to Unit
                }
            },
        )
        operations.awaitAll()

        val game = checkNotNull(store.getGame(table.id))
        val decision = store.snapshot().historyRecordingState.decisionsByMatchId[game.matchId]
        assertTrue(
            decision == HistoryRecordingDecision.EXCLUDED_CONFIG_DISABLED ||
                decision == HistoryRecordingDecision.STOPPED_CONFIG_DISABLED,
            "Concurrent operations must leave one complete policy decision",
        )
    }

    /** 已結束對局在設定關閉後仍可提交返回房間的最後一筆事件。 */
    @Test
    fun `disabling recording preserves a completed match awaiting return`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val table = FakeTableStateFactory.create()
        val game = Game(table, GameFlowConfig(), isMatchOver = true)
        store.load(
            AuthoritativeStateSnapshot(
                games = mapOf(game.id to game),
                historyRecordingState = HistoryRecordingState(
                    nextSequenceByMatchId = mapOf(game.matchId to 2L),
                    decisionsByMatchId = mapOf(game.matchId to HistoryRecordingDecision.RECORDING),
                ),
            ),
        )
        store.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = false))
        store.update { snapshot ->
            AuthoritativeStateUpdate(
                snapshot.copy(games = emptyMap()),
                Unit,
                historyDraftsByVenueId = mapOf(game.id to listOf(HistoryEventDraft(null, HistoryFact.ReturnedToRoom))),
            )
        }

        val recording = store.snapshot().historyRecordingState
        assertEquals(HistoryRecordingDecision.RECORDING, recording.decisionsByMatchId[game.matchId])
        assertTrue(recording.firstMissingSequenceByMatchId.isEmpty(), "Completed matches must not become partial")
        assertTrue(recording.pendingEvents.single().fact is HistoryFact.ReturnedToRoom, "The terminal return must remain recordable")
    }

    /** 先取得交易鎖的開局完整提交後，等待中的關閉政策才標記停止。 */
    @Test
    fun `disabling waits for an in-flight opening transaction`() = runTest {
        val store = AuthoritativeStateStore(historyRecordingEnabled = true)
        val repository = GameRepositoryImpl(store)
        val table = FakeTableStateFactory.create()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val opening = async {
            repository.updateGame(table.id, history = { _, _, _ -> listOf(matchStarted(table)) }) {
                entered.complete(Unit)
                release.await()
                Game(table, GameFlowConfig()) to Unit
            }
        }
        entered.await()
        val disabling = async { store.applyHistoryRecordingPolicy(HistoryRecordingPolicy(enabled = false)) }
        release.complete(Unit)
        opening.await()
        disabling.await()

        val recording = store.snapshot().historyRecordingState
        val game = checkNotNull(store.getGame(table.id))
        assertEquals(HistoryRecordingDecision.STOPPED_CONFIG_DISABLED, recording.decisionsByMatchId[game.matchId])
        assertTrue(recording.pendingEvents.single().fact is HistoryFact.MatchStarted, "Opening must commit before disabling")
        assertEquals(2L, recording.firstMissingSequenceByMatchId[game.matchId])
    }

    /**
     * 建立代表對局開局的歷史草稿。
     *
     * @param table 成功開局的桌況。
     * @param aiPlayerStrategyKeys 開局時由 AI 操控的玩家。
     * @return 未指派序號的開局事實。
     */
    private fun matchStarted(table: TableState, aiPlayerStrategyKeys: Map<Uuid, String> = emptyMap()): HistoryEventDraft = HistoryEventDraft(
        actorPlayerId = null,
        fact = HistoryFact.MatchStarted(table, GameFlowConfig(), aiPlayerStrategyKeys),
    )
}
