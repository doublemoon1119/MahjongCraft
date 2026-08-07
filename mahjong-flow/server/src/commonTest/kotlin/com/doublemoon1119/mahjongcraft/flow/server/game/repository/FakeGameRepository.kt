package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [GameRepository] 簡易實作。
 */
class FakeGameRepository : GameRepository {
    private val games = mutableMapOf<Uuid, TableState>()

    /** 累計 [getTableState] 被呼叫的次數，供驗證迴圈是否提前跳出（而非跑到迭代上限）等測試使用。 */
    var getTableStateCallCount: Int = 0
        private set

    override suspend fun getTableState(gameId: Uuid): TableState? {
        getTableStateCallCount++
        return games[gameId]
    }
    override suspend fun setTableState(state: TableState) {
        games[state.id] = state
    }
    override suspend fun removeTableState(gameId: Uuid) {
        games.remove(gameId)
    }

    override suspend fun <T> update(gameId: Uuid, block: suspend (TableState?) -> Pair<TableState?, T>): T {
        val (next, result) = block(games[gameId])
        if (next == null) games.remove(gameId) else games[gameId] = next
        return result
    }
}
