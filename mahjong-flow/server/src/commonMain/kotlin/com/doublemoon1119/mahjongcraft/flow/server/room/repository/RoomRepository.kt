package com.doublemoon1119.mahjongcraft.flow.server.room.repository

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
}