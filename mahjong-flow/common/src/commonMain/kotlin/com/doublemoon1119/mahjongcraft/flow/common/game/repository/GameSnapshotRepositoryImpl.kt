package com.doublemoon1119.mahjongcraft.flow.common.game.repository

import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single(binds = [GameSnapshotRepository::class])
class GameSnapshotRepositoryImpl : GameSnapshotRepository {
    private val snapshots = mutableMapOf<Uuid, MutableMap<Uuid, TableStateSnapshot>>()
    private val mutex = Mutex()

    override suspend fun getSnapshot(gameId: Uuid, observerId: Uuid): TableStateSnapshot? =
        mutex.withLock { snapshots[gameId]?.get(observerId) }

    override suspend fun setSnapshot(observerId: Uuid, snapshot: TableStateSnapshot) =
        mutex.withLock {
            snapshots.getOrPut(snapshot.id) { mutableMapOf() }[observerId] = snapshot
        }

    override suspend fun removeSnapshot(gameId: Uuid, observerId: Uuid) =
        mutex.withLock { snapshots[gameId]?.remove(observerId); Unit }

    override suspend fun getAllObservers(roomId: Uuid): Set<Uuid> =
        mutex.withLock { snapshots[roomId]?.keys.orEmpty() }
}
