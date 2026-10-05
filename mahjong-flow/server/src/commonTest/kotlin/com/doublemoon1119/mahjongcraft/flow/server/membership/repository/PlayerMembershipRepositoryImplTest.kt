package com.doublemoon1119.mahjongcraft.flow.server.membership.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [PlayerMembershipRepositoryImpl] 唯一歸屬與並發行為測試。 */
class PlayerMembershipRepositoryImplTest {
    /** 同一玩家同時競爭多個場地時只能成功占用其中一個。 */
    @Test
    fun `test concurrent claims keep exactly one table membership`() = runTest {
        val repository = PlayerMembershipRepositoryImpl()
        val playerId = Uuid.random()
        val venueIds = List(20) { Uuid.random() }

        val results = venueIds.map { venueId -> async { venueId to repository.claim(playerId, venueId) } }.awaitAll()
        val claimedVenueId = results.single { it.second }.first

        assertEquals(claimedVenueId, repository.getVenueId(playerId))
    }

    /** 舊場地的延遲 release 不得清除玩家後來建立的新歸屬。 */
    @Test
    fun `test release only removes the matching table membership`() = runTest {
        val repository = PlayerMembershipRepositoryImpl()
        val playerId = Uuid.random()
        val oldVenueId = Uuid.random()
        val newVenueId = Uuid.random()

        assertTrue(repository.claim(playerId, oldVenueId))
        repository.release(playerId, oldVenueId)
        assertTrue(repository.claim(playerId, newVenueId))
        repository.release(playerId, oldVenueId)

        assertEquals(newVenueId, repository.getVenueId(playerId))
        repository.clearAll()
        assertNull(repository.getVenueId(playerId))
    }

    /** 完整替換歸屬時應移除舊 session 的索引並載入新索引。 */
    @Test
    fun `test replace all swaps the complete membership index`() = runTest {
        val repository = PlayerMembershipRepositoryImpl()
        val oldPlayerId = Uuid.random()
        val newPlayerId = Uuid.random()
        val newVenueId = Uuid.random()
        repository.claim(oldPlayerId, Uuid.random())

        repository.replaceAll(mapOf(newPlayerId to newVenueId))

        assertNull(repository.getVenueId(oldPlayerId))
        assertEquals(newVenueId, repository.getVenueId(newPlayerId))
    }
}
