package com.doublemoon1119.mahjongcraft.application.common.room.repository

import com.doublemoon1119.mahjongcraft.domain.room.RoomSnapshot
import java.util.UUID

/**
 * 房間快照資料存取介面。
 *
 * 用於在伺服器與客戶端之間同步唯讀的房間狀態。
 */
interface RoomSnapshotRepository {
    /**
     * 根據識別碼獲取房間快照。
     *
     * @param id 房間的 UUID。
     * @return 找到的 [RoomSnapshot] 實例，若不存在則回傳 null。
     */
    suspend fun getSnapshot(id: UUID): RoomSnapshot?

    /**
     * 儲存或更新房間快照。
     *
     * @param snapshot 要存入的快照實例。
     */
    suspend fun setSnapshot(snapshot: RoomSnapshot)

    /**
     * 移除指定的快照。
     *
     * @param id 要移除的房間 UUID。
     */
    suspend fun removeSnapshot(id: UUID)
}