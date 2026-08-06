package com.doublemoon1119.mahjongcraft.flow.common.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single(binds = [RoomSnapshotRepository::class])
class RoomSnapshotRepositoryImpl : RoomSnapshotRepository {
    private val snapshots = mutableMapOf<Uuid, MutableMap<Uuid, RoomSnapshot>>()
    private val mutex = Mutex()

    override suspend fun getSnapshot(roomId: Uuid, observerId: Uuid): RoomSnapshot? = mutex.withLock { snapshots[roomId]?.get(observerId) }

    override suspend fun setSnapshot(observerId: Uuid, snapshot: RoomSnapshot) = mutex.withLock {
        snapshots.getOrPut(snapshot.id) { mutableMapOf() }[observerId] = snapshot
    }

    override suspend fun removeSnapshot(roomId: Uuid, observerId: Uuid) = mutex.withLock {
        snapshots[roomId]?.remove(observerId)
        Unit
    }

    override suspend fun getAllObservers(roomId: Uuid): Set<Uuid> = mutex.withLock { snapshots[roomId]?.keys.orEmpty() }
}
