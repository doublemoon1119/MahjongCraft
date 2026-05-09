package com.doublemoon1119.mahjongcraft.testing.application.common.room.repository

import com.doublemoon1119.mahjongcraft.application.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.domain.room.RoomSnapshot
import java.util.*

/**
 * 供測試使用的 [RoomSnapshotRepository] 簡易實作。
 */
class FakeRoomSnapshotRepository : RoomSnapshotRepository {
    private val snapshots = mutableMapOf<Pair<UUID, UUID>, RoomSnapshot>()
    override suspend fun getSnapshot(roomId: UUID, observerId: UUID): RoomSnapshot? = snapshots[roomId to observerId]
    override suspend fun setSnapshot(observerId: UUID, snapshot: RoomSnapshot) {
        snapshots[snapshot.id to observerId] = snapshot
    }

    override suspend fun removeSnapshot(roomId: UUID, observerId: UUID) {
        snapshots.remove(roomId to observerId)
    }
}