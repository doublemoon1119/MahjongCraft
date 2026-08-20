package com.doublemoon1119.mahjongcraft.flow.server.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import kotlin.uuid.Uuid

/**
 * 房間權威資料存取介面。
 *
 * 負責在伺服器端管理 [Room] 領域模型的持久化或狀態緩存。
 */
interface RoomRepository {
    /**
     * 根據唯一的識別碼獲取房間實例。
     *
     * @param id 房間的 Uuid。
     * @return 找到的 [Room] 實例，若不存在則回傳 null。
     */
    suspend fun getRoom(id: Uuid): Room?

    /**
     * 儲存或更新房間資訊。
     *
     * @param room 要存入的房間實例。
     */
    suspend fun setRoom(room: Room)

    /**
     * 移除指定的房間。
     *
     * @param id 要移除的房間 Uuid。
     */
    suspend fun removeRoom(id: Uuid)

    /** 清除目前 server session 中的所有房間；平台停止或切換世界時使用。 */
    suspend fun clearAll()

    /**
     * 以原子方式讀取並更新指定房間，確保「讀取現況、驗證業務規則、寫回」整個流程不被其他並發呼叫插入。
     *
     * 用於取代「先 [getRoom] 再 [setRoom]」的寫法，避免多個請求同時操作同一房間時，
     * 因各自持有過期的讀取結果而覆蓋彼此的變更（check-then-act 競態條件）。
     *
     * @param T 呼叫端自訂的回傳型別，通常用於攜帶驗證結果（如 [Outcome]）。
     * @param id 欲更新的房間 Uuid。
     * @param block 根據目前的房間狀態（不存在時為 null）計算「欲寫入的新狀態」與「回傳給呼叫端的結果」。
     *              回傳的房間為 null 時代表該房間應被移除；若無需變更，回傳原本傳入的房間即可（等同無操作）。
     * @return [block] 計算出的結果。
     */
    suspend fun <T> update(id: Uuid, block: suspend (Room?) -> Pair<Room?, T>): T
}
