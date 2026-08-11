package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [GameRepository] 簡易實作。
 */
class FakeGameRepository : GameRepository {
    private val games = mutableMapOf<Uuid, Game>()

    override suspend fun getGame(gameId: Uuid): Game? = games[gameId]

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
        games[state.id] = games[state.id]?.copy(tableState = state) ?: Game(state, GameFlowConfig())
    }
    override suspend fun removeTableState(gameId: Uuid) {
        games.remove(gameId)
    }
    override suspend fun clearAll() {
        games.clear()
    }

    override suspend fun <T> update(gameId: Uuid, block: suspend (TableState?) -> Pair<TableState?, T>): T {
        val current = games[gameId]
        val (next, result) = block(current?.tableState)
        if (next == null) games.remove(gameId) else games[gameId] = current?.copy(tableState = next) ?: Game(next, GameFlowConfig())
        return result
    }
}
