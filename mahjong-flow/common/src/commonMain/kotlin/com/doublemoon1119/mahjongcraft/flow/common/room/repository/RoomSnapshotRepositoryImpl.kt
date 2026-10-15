package com.doublemoon1119.mahjongcraft.flow.common.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/** 以記憶體快照依房間與觀察者分開保存，並以互斥鎖保護存取的 repository 實作。 */
class RoomSnapshotRepositoryImpl : RoomSnapshotRepository {
    /** 依房間 ID、觀察者 ID 儲存各自最近發布的房間快照。 */
    private val snapshots = mutableMapOf<Uuid, MutableMap<Uuid, RoomSnapshot>>()

    /** 序列化所有快照讀寫操作的互斥鎖。 */
    private val mutex = Mutex()

    override suspend fun getSnapshot(roomId: Uuid, observerId: Uuid): RoomSnapshot? = mutex.withLock { snapshots[roomId]?.get(observerId) }

    override suspend fun setSnapshot(observerId: Uuid, snapshot: RoomSnapshot) = mutex.withLock {
        snapshots.getOrPut(snapshot.id) { mutableMapOf() }[observerId] = snapshot
    }

    override suspend fun removeSnapshot(roomId: Uuid, observerId: Uuid) = mutex.withLock {
        snapshots[roomId]?.remove(observerId)
        Unit
    }

    override suspend fun removeSnapshotIf(
        roomId: Uuid,
        observerId: Uuid,
        isStillValid: () -> Boolean,
    ): Boolean = mutex.withLock {
        if (!isStillValid()) return@withLock false
        snapshots[roomId]?.remove(observerId)
        true
    }

    override suspend fun getAllObservers(roomId: Uuid): Set<Uuid> = mutex.withLock { snapshots[roomId]?.keys.orEmpty().toSet() }

    override suspend fun clearAll() = mutex.withLock { snapshots.clear() }
}
