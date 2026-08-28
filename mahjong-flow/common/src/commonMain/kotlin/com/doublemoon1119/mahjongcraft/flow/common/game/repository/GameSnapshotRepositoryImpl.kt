package com.doublemoon1119.mahjongcraft.flow.common.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single(binds = [GameSnapshotRepository::class])
class GameSnapshotRepositoryImpl : GameSnapshotRepository {
    private val snapshots = mutableMapOf<Uuid, MutableMap<Uuid, TableStateSnapshot>>()
    private val preparationSnapshots = mutableMapOf<Uuid, MutableMap<Uuid, RoundPreparationSnapshot>>()
    private val mutex = Mutex()

    override suspend fun getSnapshot(gameId: Uuid, observerId: Uuid): TableStateSnapshot? = mutex.withLock { snapshots[gameId]?.get(observerId) }

    override suspend fun setSnapshot(observerId: Uuid, snapshot: TableStateSnapshot) = mutex.withLock {
        snapshots.getOrPut(snapshot.id) { mutableMapOf() }[observerId] = snapshot
    }

    override suspend fun getRoundPreparationSnapshot(gameId: Uuid, observerId: Uuid): RoundPreparationSnapshot? = mutex.withLock { preparationSnapshots[gameId]?.get(observerId) }

    override suspend fun setRoundPreparationSnapshot(
        gameId: Uuid,
        observerId: Uuid,
        snapshot: RoundPreparationSnapshot?,
    ) = mutex.withLock {
        if (snapshot == null) {
            preparationSnapshots[gameId]?.remove(observerId)
        } else {
            preparationSnapshots.getOrPut(gameId) { mutableMapOf() }[observerId] = snapshot
        }
        Unit
    }

    override suspend fun removeSnapshot(gameId: Uuid, observerId: Uuid) = mutex.withLock {
        snapshots[gameId]?.remove(observerId)
        preparationSnapshots[gameId]?.remove(observerId)
        Unit
    }

    override suspend fun getAllObservers(gameId: Uuid): Set<Uuid> = mutex.withLock { snapshots[gameId]?.keys.orEmpty() }

    override suspend fun clearAll() = mutex.withLock {
        snapshots.clear()
        preparationSnapshots.clear()
    }
}
