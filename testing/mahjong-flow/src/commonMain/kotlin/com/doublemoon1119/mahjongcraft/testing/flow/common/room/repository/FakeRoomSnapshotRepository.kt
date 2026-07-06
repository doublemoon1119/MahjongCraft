package com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [RoomSnapshotRepository] 簡易實作。
 *
 * 透過記憶體內的映射表模擬觀察者狀態。
 */
class FakeRoomSnapshotRepository : RoomSnapshotRepository {
    /** 儲存快照的映射表，鍵為房間 Uuid 與觀察者 Uuid 的組合。 */
    private val snapshots = mutableMapOf<Pair<Uuid, Uuid>, RoomSnapshot>()

    override suspend fun getSnapshot(roomId: Uuid, observerId: Uuid): RoomSnapshot? =
        snapshots[roomId to observerId]

    override suspend fun setSnapshot(observerId: Uuid, snapshot: RoomSnapshot) {
        snapshots[snapshot.id to observerId] = snapshot
    }

    override suspend fun removeSnapshot(roomId: Uuid, observerId: Uuid) {
        snapshots.remove(roomId to observerId)
    }

    override suspend fun getAllObservers(roomId: Uuid): Set<Uuid> {
        return snapshots.keys
            .filter { it.first == roomId }
            .map { it.second }
            .toSet()
    }
}