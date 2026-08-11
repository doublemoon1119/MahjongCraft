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

    override suspend fun <T> update(gameId: Uuid, block: suspend (TableState?) -> Pair<TableState?, T>): T = store.update { state ->
        val currentGame = state.games[gameId]
        val (next, result) = block(currentGame?.tableState)
        val games = when {
            next == null -> state.games - gameId
            currentGame == null -> state.games + (gameId to Game(next, GameFlowConfig()))
            else -> state.games + (gameId to currentGame.copy(tableState = next))
        }
        AuthoritativeStateUpdate(state.copy(games = games), result)
    }
}
