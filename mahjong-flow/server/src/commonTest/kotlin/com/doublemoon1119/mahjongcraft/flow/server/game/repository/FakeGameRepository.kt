package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.coroutines.currentCoroutineContext
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [GameRepository] 簡易實作。
 *
 * 權威寫入與正式實作一樣遵守 [GameRepository.withExpectedGame] 的範圍；[setGame] 是測試直接設定狀態用，不受範圍限制。
 */
class FakeGameRepository : GameRepository {
    private val games = mutableMapOf<Uuid, Game>()

    /** 權威寫入（[updateGame]、[update]、[setTableState]、[removeTableState]、[clearAll]）的累計次數；[setGame] 不計入。 */
    var writeCount: Int = 0
        private set

    /** 測試中由交易 callback 產生的歷史草稿。 */
    val historyDrafts: MutableList<HistoryEventDraft> = mutableListOf()

    override suspend fun getGame(gameId: Uuid): Game? = games[gameId]

    override suspend fun getAllGameIds(): Set<Uuid> = games.keys.toSet()

    /** 直接寫入包含流程設定的 [Game]，供需驗證 flow policy 的測試使用。 */
    suspend fun setGame(game: Game) {
        games[game.id] = game
    }

    /** 累計 [getTableState] 被呼叫的次數，供驗證迴圈是否提前跳出（而非跑到迭代上限）等測試使用。 */
    var getTableStateCallCount: Int = 0
        private set

    override suspend fun getTableState(gameId: Uuid): TableState? {
        getTableStateCallCount++
        return games[gameId]?.tableState
    }
    override suspend fun setTableState(state: TableState) {
        expectedGameScopeFor(state.id)?.checkBeforeWrite(games[state.id])
        writeCount++
        games[state.id] = games[state.id]?.copy(tableState = state) ?: Game(state, GameFlowConfig())
    }

    /** 寫入 [state] 並指定由 AI 操控的玩家，供需要 AI 座位的測試使用。 */
    suspend fun setTableState(state: TableState, aiPlayerStrategyKeys: Map<Uuid, String>) {
        setTableState(state)
        games[state.id] = games.getValue(state.id).copy(aiPlayerStrategyKeys = aiPlayerStrategyKeys)
    }
    override suspend fun removeTableState(gameId: Uuid) {
        expectedGameScopeFor(gameId)?.checkBeforeWrite(games[gameId])
        writeCount++
        games.remove(gameId)
    }
    override suspend fun clearAll() {
        currentCoroutineContext()[ExpectedGameScope]?.let { scope -> scope.checkBeforeWrite(games[scope.gameId]) }
        writeCount++
        games.clear()
    }

    override suspend fun <T> updateGame(
        gameId: Uuid,
        history: (Game?, Game?, T) -> List<HistoryEventDraft>,
        block: suspend (Game?) -> Pair<Game?, T>,
    ): T {
        val previous = games[gameId]
        expectedGameScopeFor(gameId)?.checkBeforeWrite(previous)
        writeCount++
        val (next, result) = block(previous)
        if (next == null) games.remove(gameId) else games[gameId] = next
        historyDrafts += history(previous, next, result)
        return result
    }

    override suspend fun <T> update(
        gameId: Uuid,
        history: (TableState?, TableState?, T) -> List<HistoryEventDraft>,
        block: suspend (TableState?) -> Pair<TableState?, T>,
    ): T = updateGame(gameId, history = { previous, next, result ->
        history(previous?.tableState, next?.tableState, result)
    }) { current ->
        val (nextTableState, result) = block(current?.tableState)
        val nextGame = when {
            nextTableState == null -> null
            current == null -> Game(nextTableState, GameFlowConfig())
            else -> current.copy(tableState = nextTableState)
        }
        nextGame to result
    }

    override suspend fun <T> withExpectedGame(gameId: Uuid, expectedGame: Game, command: suspend () -> T): ExpectedGameResult<T> = runWithExpectedGame(gameId, expectedGame, command)
}
