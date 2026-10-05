package com.doublemoon1119.mahjongcraft.flow.server.membership.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 以互斥鎖保護玩家唯一場地歸屬的記憶體實作。 */
@Single(binds = [PlayerMembershipRepository::class])
class PlayerMembershipRepositoryImpl : PlayerMembershipRepository {
    private val venueIdsByPlayerId = mutableMapOf<Uuid, Uuid>()
    private val mutex = Mutex()

    override suspend fun claim(playerId: Uuid, venueId: Uuid): Boolean = mutex.withLock {
        val existingVenueId = venueIdsByPlayerId[playerId]
        if (existingVenueId != null && existingVenueId != venueId) return@withLock false
        venueIdsByPlayerId[playerId] = venueId
        true
    }

    override suspend fun getVenueId(playerId: Uuid): Uuid? = mutex.withLock { venueIdsByPlayerId[playerId] }

    override suspend fun release(playerId: Uuid, venueId: Uuid) = mutex.withLock {
        if (venueIdsByPlayerId[playerId] == venueId) venueIdsByPlayerId.remove(playerId)
        Unit
    }

    override suspend fun replaceAll(venueIdsByPlayerId: Map<Uuid, Uuid>) = mutex.withLock {
        this.venueIdsByPlayerId.clear()
        this.venueIdsByPlayerId.putAll(venueIdsByPlayerId)
    }

    override suspend fun clearAll() = mutex.withLock { venueIdsByPlayerId.clear() }
}
