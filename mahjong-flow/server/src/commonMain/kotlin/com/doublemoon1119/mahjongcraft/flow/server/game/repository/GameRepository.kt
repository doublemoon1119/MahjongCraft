package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 伺服器端桌況數據倉庫。
 *
 * 負責管理伺服器運行期間遊戲狀態的存取與追蹤。
 */
interface GameRepository {
    /** 取得包含流程設定的完整權威 [Game]。 */
    suspend fun getGame(gameId: Uuid): Game?

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

    /** 清除目前 server session 中的所有遊戲狀態；平台停止或切換世界時使用。 */
    suspend fun clearAll()

    /**
     * 以原子方式讀取並更新包含桌況與流程 runtime 狀態的完整 [Game]。
     *
     * @param T 呼叫端自訂的回傳型別。
     * @param gameId 欲更新的遊戲唯一識別碼。
     * @param block 根據目前完整遊戲計算欲寫入的遊戲與回傳結果；遊戲為 null 時代表移除。
     * @return [block] 計算出的結果。
     */
    suspend fun <T> updateGame(gameId: Uuid, block: suspend (Game?) -> Pair<Game?, T>): T

    /**
     * 以原子方式讀取並更新指定遊戲的桌況，確保「讀取現況、驗證業務規則、寫回」整個流程不被其他並發呼叫插入。
     *
     * 用於取代「先 [getTableState] 再 [setTableState]」的寫法，避免多個請求同時操作同一遊戲時，
     * 因各自持有過期的讀取結果而覆蓋彼此的變更（check-then-act 競態條件）。
     *
     * @param T 呼叫端自訂的回傳型別，通常用於攜帶驗證結果（如 [com.doublemoon1119.mahjongcraft.flow.common.result.Outcome]）。
     * @param gameId 欲更新的遊戲唯一識別碼。
     * @param block 根據目前的桌況（不存在時為 null）計算「欲寫入的新狀態」與「回傳給呼叫端的結果」。
     *              回傳的桌況為 null 時代表該遊戲應被移除；若無需變更，回傳原本傳入的桌況即可（等同無操作）。
     * @return [block] 計算出的結果。
     */
    suspend fun <T> update(gameId: Uuid, block: suspend (TableState?) -> Pair<TableState?, T>): T
}
