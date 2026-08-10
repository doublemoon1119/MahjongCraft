package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single(binds = [GameRepository::class])
class GameRepositoryImpl(
    private val store: AuthoritativeStateStore,
) : GameRepository {
    override suspend fun getTableState(gameId: Uuid): TableState? = store.getGame(gameId)

    override suspend fun setTableState(state: TableState) = store.update { current ->
        AuthoritativeStateUpdate(current.copy(games = current.games + (state.id to state)), Unit)
    }

    override suspend fun removeTableState(gameId: Uuid) = store.update { state ->
        AuthoritativeStateUpdate(state.copy(games = state.games - gameId), Unit)
    }

    override suspend fun clearAll() = store.update { state ->
        AuthoritativeStateUpdate(state.copy(games = emptyMap()), Unit)
    }

    override suspend fun <T> update(gameId: Uuid, block: suspend (TableState?) -> Pair<TableState?, T>): T = store.update { state ->
        val (next, result) = block(state.games[gameId])
        val games = if (next == null) state.games - gameId else state.games + (gameId to next)
        AuthoritativeStateUpdate(state.copy(games = games), result)
    }
}
