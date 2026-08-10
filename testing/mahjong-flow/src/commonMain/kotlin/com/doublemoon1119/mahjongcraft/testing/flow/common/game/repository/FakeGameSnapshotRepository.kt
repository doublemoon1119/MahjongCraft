package com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 供測試使用的 [GameSnapshotRepository] 簡易實作。
 *
 * 透過記憶體內的映射表模擬觀察者狀態。
 */
class FakeGameSnapshotRepository : GameSnapshotRepository {
    /** 儲存快照的映射表，鍵為對局 Uuid 與觀察者 Uuid 的組合。 */
    private val snapshots = mutableMapOf<Pair<Uuid, Uuid>, TableStateSnapshot>()

    override suspend fun getSnapshot(gameId: Uuid, observerId: Uuid): TableStateSnapshot? = snapshots[gameId to observerId]

    override suspend fun setSnapshot(observerId: Uuid, snapshot: TableStateSnapshot) {
        snapshots[snapshot.id to observerId] = snapshot
    }

    override suspend fun removeSnapshot(gameId: Uuid, observerId: Uuid) {
        snapshots.remove(gameId to observerId)
    }

    override suspend fun getAllObservers(gameId: Uuid): Set<Uuid> = snapshots.keys
        .filter { it.first == gameId }
        .map { it.second }
        .toSet()

    override suspend fun clearAll() {
        snapshots.clear()
    }
}
