package com.doublemoon1119.mahjongcraft.application.common.room.repository

import com.doublemoon1119.mahjongcraft.domain.room.RoomSnapshot
import java.util.*

/**
 * 房間快照資料存取介面。
 *
 * 負責管理房間快照的生命週期與同步。
 * 此介面定義了針對特定房間狀態的觀察者行為，不涉及具體的硬體或平台實作細節。
 */
interface RoomSnapshotRepository {
    /**
     * 獲取特定觀察者針對某房間的快照。
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

    /**
     * 獲取目前所有正在觀察該房間的所有觀察者 UUID 集合。
     *
     * @param roomId 房間的 UUID。
     * @return 觀察該房間的所有玩家 UUID 集合。
     */
    suspend fun getAllObservers(roomId: UUID): Set<UUID>
}