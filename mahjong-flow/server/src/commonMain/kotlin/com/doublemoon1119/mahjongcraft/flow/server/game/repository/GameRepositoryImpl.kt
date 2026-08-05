package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single(binds = [GameRepository::class])
class GameRepositoryImpl : GameRepository {
    private val tableStates = mutableMapOf<Uuid, TableState>()
    private val mutex = Mutex()

    override suspend fun getTableState(gameId: Uuid): TableState? = mutex.withLock { tableStates[gameId] }

    override suspend fun setTableState(state: TableState) = mutex.withLock { tableStates[state.id] = state }

    override suspend fun removeTableState(gameId: Uuid) = mutex.withLock { tableStates.remove(gameId); Unit }

    override suspend fun <T> update(gameId: Uuid, block: (TableState?) -> Pair<TableState?, T>): T = mutex.withLock {
        val (next, result) = block(tableStates[gameId])
        if (next == null) tableStates.remove(gameId) else tableStates[gameId] = next
        result
    }
}
