package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 伺服器端桌況數據倉庫。
 *
 * 負責管理伺服器運行期間遊戲狀態的存取與追蹤。
 */
interface GameRepository {
    /**
     * 獲取指定遊戲 ID 的 [TableState]。
     *
     * @param gameId 遊戲的唯一識別碼。
     * @return 遊戲桌狀態物件，若不存在則回傳 null。
     */
    suspend fun getTableState(gameId: Uuid): TableState?

    /**
     * 設置或更新遊戲桌狀態。
     *
     * @param state 要管理的狀態物件。
     */
    suspend fun setTableState(state: TableState)

    /**
     * 移除指定遊戲的狀態。
     *
     * @param gameId 遊戲的唯一識別碼。
     */
    suspend fun removeTableState(gameId: Uuid)
}