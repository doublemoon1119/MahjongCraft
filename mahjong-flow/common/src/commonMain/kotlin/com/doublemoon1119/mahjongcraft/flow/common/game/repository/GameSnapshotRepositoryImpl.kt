package com.doublemoon1119.mahjongcraft.flow.common.game.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/** 以互斥鎖保護記憶體內桌況與規則選擇快照的 repository 實作。 */
class GameSnapshotRepositoryImpl : GameSnapshotRepository {
    /** 依遊戲及觀看者識別碼保存的桌況快照。 */
    private val snapshots = mutableMapOf<Uuid, MutableMap<Uuid, TableStateSnapshot>>()

    /** 依遊戲及觀看者識別碼保存的規則選擇快照。 */
    private val ruleDecisionSnapshots = mutableMapOf<Uuid, MutableMap<Uuid, RuleDecisionSnapshot>>()

    /** 保護兩種快照對應表的互斥鎖。 */
    private val mutex = Mutex()

    override suspend fun getSnapshot(gameId: Uuid, observerId: Uuid): TableStateSnapshot? = mutex.withLock { snapshots[gameId]?.get(observerId) }

    override suspend fun setSnapshot(observerId: Uuid, snapshot: TableStateSnapshot) = mutex.withLock {
        snapshots.getOrPut(snapshot.id) { mutableMapOf() }[observerId] = snapshot
    }

    override suspend fun getRuleDecisionSnapshot(gameId: Uuid, observerId: Uuid): RuleDecisionSnapshot? = mutex.withLock { ruleDecisionSnapshots[gameId]?.get(observerId) }

    override suspend fun setRuleDecisionSnapshot(
        gameId: Uuid,
        observerId: Uuid,
        snapshot: RuleDecisionSnapshot?,
    ) = mutex.withLock {
        if (snapshot == null) {
            ruleDecisionSnapshots[gameId]?.remove(observerId)
        } else {
            ruleDecisionSnapshots.getOrPut(gameId) { mutableMapOf() }[observerId] = snapshot
        }
        Unit
    }

    override suspend fun removeSnapshot(gameId: Uuid, observerId: Uuid) = mutex.withLock {
        snapshots[gameId]?.remove(observerId)
        ruleDecisionSnapshots[gameId]?.remove(observerId)
        Unit
    }

    override suspend fun removeSnapshotIf(
        gameId: Uuid,
        observerId: Uuid,
        isStillValid: () -> Boolean,
    ): Boolean = mutex.withLock {
        if (!isStillValid()) return@withLock false
        snapshots[gameId]?.remove(observerId)
        ruleDecisionSnapshots[gameId]?.remove(observerId)
        true
    }

    override suspend fun getAllObservers(gameId: Uuid): Set<Uuid> = mutex.withLock { snapshots[gameId]?.keys.orEmpty().toSet() }

    override suspend fun clearAll() = mutex.withLock {
        snapshots.clear()
        ruleDecisionSnapshots.clear()
    }
}
