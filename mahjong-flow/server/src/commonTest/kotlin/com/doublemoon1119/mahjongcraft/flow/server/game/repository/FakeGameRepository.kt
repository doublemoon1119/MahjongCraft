package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [GameRepository] 簡易實作。
 */
class FakeGameRepository : GameRepository {
    private val games = mutableMapOf<Uuid, TableState>()
    override suspend fun getTableState(gameId: Uuid): TableState? = games[gameId]
    override suspend fun setTableState(state: TableState) { games[state.id] = state }
    override suspend fun removeTableState(gameId: Uuid) { games.remove(gameId) }

    override suspend fun <T> update(gameId: Uuid, block: suspend (TableState?) -> Pair<TableState?, T>): T {
        val (next, result) = block(games[gameId])
        if (next == null) games.remove(gameId) else games[gameId] = next
        return result
    }
}
