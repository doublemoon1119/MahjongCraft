package com.doublemoon1119.mahjongcraft.flow.server.membership.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 以互斥鎖保護玩家唯一桌子歸屬的記憶體實作。 */
@Single(binds = [PlayerMembershipRepository::class])
class PlayerMembershipRepositoryImpl : PlayerMembershipRepository {
    private val tableIdsByPlayerId = mutableMapOf<Uuid, Uuid>()
    private val mutex = Mutex()

    override suspend fun claim(playerId: Uuid, tableId: Uuid): Boolean = mutex.withLock {
        val existingTableId = tableIdsByPlayerId[playerId]
        if (existingTableId != null && existingTableId != tableId) return@withLock false
        tableIdsByPlayerId[playerId] = tableId
        true
    }

    override suspend fun getTableId(playerId: Uuid): Uuid? = mutex.withLock { tableIdsByPlayerId[playerId] }

    override suspend fun release(playerId: Uuid, tableId: Uuid) = mutex.withLock {
        if (tableIdsByPlayerId[playerId] == tableId) tableIdsByPlayerId.remove(playerId)
        Unit
    }

    override suspend fun replaceAll(tableIdsByPlayerId: Map<Uuid, Uuid>) = mutex.withLock {
        this.tableIdsByPlayerId.clear()
        this.tableIdsByPlayerId.putAll(tableIdsByPlayerId)
    }

    override suspend fun clearAll() = mutex.withLock { tableIdsByPlayerId.clear() }
}
