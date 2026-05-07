package com.doublemoon1119.mahjongcraft.application.common.room.repository

import com.doublemoon1119.mahjongcraft.domain.room.RoomSnapshot
import java.util.UUID

/**
 * 房間快照資料存取介面。
 *
 * 用於在伺服器與客戶端之間同步針對特定觀察者的唯讀房間狀態。
 */
interface RoomSnapshotRepository {
    /**
     * 獲取特定觀察者所看見的房間快照。
     *
     * @param roomId 房間的 UUID。
     * @param observerId 觀察者的 UUID。
     * @return 找到的 [RoomSnapshot] 實例，若不存在則回傳 null。
     */
    suspend fun getSnapshot(roomId: UUID, observerId: UUID): RoomSnapshot?

    /**
     * 儲存或更新特定觀察者的房間快照。
     *
     * @param observerId 接收此快照的觀察者 UUID。
     * @param snapshot 要存入的快照實例。
     */
    suspend fun setSnapshot(observerId: UUID, snapshot: RoomSnapshot)

    /**
     * 移除特定觀察者的房間快照。
     *
     * @param roomId 房間的 UUID。
     * @param observerId 觀察者的 UUID。
     */
    suspend fun removeSnapshot(roomId: UUID, observerId: UUID)
}