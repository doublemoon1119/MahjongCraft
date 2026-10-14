package com.doublemoon1119.mahjongcraft.flow.common.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import kotlin.uuid.Uuid

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
     * @param roomId 房間的 Uuid。
     * @param observerId 觀察者的 Uuid。
     * @return 找到的 [RoomSnapshot] 實例，若不存在則回傳 null。
     */
    suspend fun getSnapshot(roomId: Uuid, observerId: Uuid): RoomSnapshot?

    /**
     * 儲存或更新特定觀察者的房間快照。
     *
     * @param observerId 接收此快照的觀察者 Uuid。
     * @param snapshot 要存入的快照實例。
     */
    suspend fun setSnapshot(observerId: Uuid, snapshot: RoomSnapshot)

    /**
     * 移除特定觀察者的房間快照。
     *
     * @param roomId 房間的 Uuid。
     * @param observerId 觀察者的 Uuid。
     */
    suspend fun removeSnapshot(roomId: Uuid, observerId: Uuid)

    /**
     * 在 repository 互斥鎖內確認來源仍有效後移除快照。
     *
     * 條件只能讀取已發布資料，不得阻塞、重新呼叫此 repository 或修改來源。
     *
     * @param roomId 房間的 Uuid。
     * @param observerId 觀察者的 Uuid。
     * @param isStillValid 鎖內刪除前確認來源仍屬於這次工作的條件。
     * @return 條件成立並完成這次移除時為 true；條件不成立時不修改資料並回傳 false。
     */
    suspend fun removeSnapshotIf(
        roomId: Uuid,
        observerId: Uuid,
        isStillValid: () -> Boolean,
    ): Boolean

    /**
     * 獲取目前所有正在觀察該房間的所有觀察者 Uuid 集合。
     *
     * @param roomId 房間的 Uuid。
     * @return 觀察該房間的所有觀察者 Uuid 集合。
     */
    suspend fun getAllObservers(roomId: Uuid): Set<Uuid>

    /** 清除目前 server session 的所有房間 observer 快照。 */
    suspend fun clearAll()
}
