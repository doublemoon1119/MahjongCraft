package com.doublemoon1119.mahjongcraft.testing.application.common.room.repository

import com.doublemoon1119.mahjongcraft.application.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.application.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.domain.room.RoomSnapshot
import java.util.*

/**
 * 供測試使用的 [RoomSnapshotRepository] 簡易實作。
 *
 * 此實作額外紀錄了最後一次移除快照的原因，以便在測試中驗證業務邏輯的正確性。
 */
class FakeRoomSnapshotRepository : RoomSnapshotRepository {
    private val snapshots = mutableMapOf<Pair<UUID, UUID>, RoomSnapshot>()

    /** 紀錄每個觀察者在特定房間最後一次離開的原因。 */
    private val lastLeaveReasons = mutableMapOf<Pair<UUID, UUID>, LeaveReason>()

    override suspend fun getSnapshot(roomId: UUID, observerId: UUID): RoomSnapshot? =
        snapshots[roomId to observerId]

    override suspend fun setSnapshot(observerId: UUID, snapshot: RoomSnapshot) {
        snapshots[snapshot.id to observerId] = snapshot
    }

    /**
     * 移除快照並記錄移除原因。
     *
     * @param roomId 房間的 UUID。
     * @param observerId 觀察者的 UUID。
     * @param reason 移除原因。
     */
    override suspend fun removeSnapshot(roomId: UUID, observerId: UUID, reason: LeaveReason) {
        snapshots.remove(roomId to observerId)
        lastLeaveReasons[roomId to observerId] = reason
    }

    /**
     * 獲取指定玩家在特定房間的最後離開原因，僅供測試斷言使用。
     */
    fun getLastLeaveReason(roomId: UUID, observerId: UUID): LeaveReason? = lastLeaveReasons[roomId to observerId]
}