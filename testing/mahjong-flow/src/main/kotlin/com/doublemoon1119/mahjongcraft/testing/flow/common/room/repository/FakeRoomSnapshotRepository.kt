package com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.logic.room.RoomSnapshot
import java.util.*

/**
 * 供測試使用的 [RoomSnapshotRepository] 簡易實作。
 *
 * 透過記憶體內的映射表模擬觀察者狀態。
 */
class FakeRoomSnapshotRepository : RoomSnapshotRepository {
    /** 儲存快照的映射表，鍵為房間 UUID 與觀察者 UUID 的組合。 */
    private val snapshots = mutableMapOf<Pair<UUID, UUID>, RoomSnapshot>()

    override suspend fun getSnapshot(roomId: UUID, observerId: UUID): RoomSnapshot? =
        snapshots[roomId to observerId]

    override suspend fun setSnapshot(observerId: UUID, snapshot: RoomSnapshot) {
        snapshots[snapshot.id to observerId] = snapshot
    }

    override suspend fun removeSnapshot(roomId: UUID, observerId: UUID) {
        snapshots.remove(roomId to observerId)
    }

    override suspend fun getAllObservers(roomId: UUID): Set<UUID> {
        return snapshots.keys
            .filter { it.first == roomId }
            .map { it.second }
            .toSet()
    }
}