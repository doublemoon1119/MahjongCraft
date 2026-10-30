package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.component.AuthoritativeComponentValidator
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.coroutines.currentCoroutineContext
import kotlin.uuid.Uuid

/**
 * 透過權威交易讀寫對局，並在對局實際改變時建立本次交易的事實；是否寫入歷史由 [store] 依記錄政策決定。
 *
 * @property store 共用狀態、記錄資格與歷史提交的交易邊界。
 * @property componentValidator 在任何候選桌況寫入前驗證其完整組件。
 */
class GameRepositoryImpl(
    private val store: AuthoritativeStateStore,
    private val componentValidator: AuthoritativeComponentValidator,
) : GameRepository {
    override suspend fun getGame(gameId: Uuid) = store.getGame(gameId)

    override suspend fun getAllGameIds(): Set<Uuid> = store.snapshot().games.keys

    override suspend fun getTableState(gameId: Uuid): TableState? = store.getGame(gameId)?.tableState

    override suspend fun setTableState(state: TableState) = store.update { current ->
        expectedGameScopeFor(state.id)?.checkBeforeWrite(current.games[state.id])
        validateComponents(state)
        val game = current.games[state.id]?.copy(tableState = state) ?: Game(state, GameFlowConfig())
        AuthoritativeStateUpdate(current.copy(games = current.games + (state.id to game)), Unit)
    }

    override suspend fun removeTableState(gameId: Uuid) = store.update { state ->
        expectedGameScopeFor(gameId)?.checkBeforeWrite(state.games[gameId])
        AuthoritativeStateUpdate(state.copy(games = state.games - gameId), Unit)
    }

    override suspend fun clearAll() = store.update { state ->
        currentCoroutineContext()[ExpectedGameScope]?.let { scope -> scope.checkBeforeWrite(state.games[scope.gameId]) }
        AuthoritativeStateUpdate(state.copy(games = emptyMap()), Unit)
    }

    override suspend fun <T> updateGame(
        gameId: Uuid,
        history: (Game?, Game?, T) -> List<HistoryEventDraft>,
        block: suspend (Game?) -> Pair<Game?, T>,
    ): T = store.update { state ->
        val previous = state.games[gameId]
        expectedGameScopeFor(gameId)?.checkBeforeWrite(previous)
        val (next, result) = block(previous)
        if (next != previous && next != null) validateComponents(next.tableState)
        val games = when {
            next == null -> state.games - gameId
            else -> state.games + (gameId to next)
        }
        val recording = if ((next ?: previous) != null && next != previous) {
            runCatching { history(previous, next, result) }
        } else {
            Result.success(emptyList())
        }
        val drafts = recording.getOrDefault(emptyList())
        AuthoritativeStateUpdate(
            state.copy(games = games),
            result,
            historyDraftsByVenueId = if (drafts.isEmpty()) emptyMap() else mapOf(gameId to drafts),
            historyRecordingFailures = if (recording.isFailure) setOf(gameId) else emptySet(),
        )
    }

    override suspend fun <T> update(
        gameId: Uuid,
        history: (TableState?, TableState?, T) -> List<HistoryEventDraft>,
        block: suspend (TableState?) -> Pair<TableState?, T>,
    ): T = updateGame(
        gameId,
        history = { previous, next, result -> history(previous?.tableState, next?.tableState, result) },
    ) { currentGame ->
        val (nextTableState, result) = block(currentGame?.tableState)
        val nextGame = when {
            nextTableState == null -> null
            currentGame == null -> Game(nextTableState, GameFlowConfig())
            else -> currentGame.copy(tableState = nextTableState)
        }
        nextGame to result
    }

    /**
     * 將候選桌況的組件驗證失敗回報為規則契約錯誤，不提交部分資料。
     *
     * @param state 欲寫入的完整桌況。
     */
    private fun validateComponents(state: TableState) {
        try {
            componentValidator.validate(state)
        } catch (failure: IllegalArgumentException) {
            throw RuleContractViolationException(
                GameAbortReasonIds.RULE_EXECUTION_FAILED,
                "Invalid authoritative components for game ${state.id}: ${failure.message}",
            )
        }
    }

    override suspend fun <T> withExpectedGame(gameId: Uuid, expectedGame: Game, command: suspend () -> T): ExpectedGameResult<T> = runWithExpectedGame(gameId, expectedGame, command)
}
