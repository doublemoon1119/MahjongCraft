package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single(binds = [GameRepository::class])
class GameRepositoryImpl(
    private val store: AuthoritativeStateStore,
) : GameRepository {
    override suspend fun getGame(gameId: Uuid) = store.getGame(gameId)

    override suspend fun getAllGameIds(): Set<Uuid> = store.snapshot().games.keys

    override suspend fun getTableState(gameId: Uuid): TableState? = store.getGame(gameId)?.tableState

    override suspend fun setTableState(state: TableState) = store.update { current ->
        val game = current.games[state.id]?.copy(tableState = state) ?: Game(state, GameFlowConfig())
        AuthoritativeStateUpdate(current.copy(games = current.games + (state.id to game)), Unit)
    }

    override suspend fun removeTableState(gameId: Uuid) = store.update { state ->
        AuthoritativeStateUpdate(state.copy(games = state.games - gameId), Unit)
    }

    override suspend fun clearAll() = store.update { state ->
        AuthoritativeStateUpdate(state.copy(games = emptyMap()), Unit)
    }

    override suspend fun <T> updateGame(gameId: Uuid, block: suspend (Game?) -> Pair<Game?, T>): T = store.update { state ->
        val (next, result) = block(state.games[gameId])
        val games = when {
            next == null -> state.games - gameId
            else -> state.games + (gameId to next)
        }
        AuthoritativeStateUpdate(state.copy(games = games), result)
    }

    override suspend fun <T> update(gameId: Uuid, block: suspend (TableState?) -> Pair<TableState?, T>): T = updateGame(gameId) { currentGame ->
        val (nextTableState, result) = block(currentGame?.tableState)
        val nextGame = when {
            nextTableState == null -> null
            currentGame == null -> Game(nextTableState, GameFlowConfig())
            else -> currentGame.copy(tableState = nextTableState)
        }
        nextGame to result
    }
}
