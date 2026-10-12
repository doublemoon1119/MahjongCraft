package com.doublemoon1119.mahjongcraft.flow.common.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 以互斥鎖保護記憶體內桌況與開局準備快照的 repository 實作。 */
@Single(binds = [GameSnapshotRepository::class])
class GameSnapshotRepositoryImpl : GameSnapshotRepository {
    /** 依遊戲及觀看者識別碼保存的桌況快照。 */
    private val snapshots = mutableMapOf<Uuid, MutableMap<Uuid, TableStateSnapshot>>()

    /** 依遊戲及觀看者識別碼保存的開局準備快照。 */
    private val preparationSnapshots = mutableMapOf<Uuid, MutableMap<Uuid, RoundPreparationSnapshot>>()

    /** 保護兩種快照對應表的互斥鎖。 */
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
