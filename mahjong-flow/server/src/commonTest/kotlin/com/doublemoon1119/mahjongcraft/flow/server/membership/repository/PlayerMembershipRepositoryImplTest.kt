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
    /** 同一玩家同時競爭多張桌子時只能成功占用其中一張。 */
    @Test
    fun `test concurrent claims keep exactly one table membership`() = runTest {
        val repository = PlayerMembershipRepositoryImpl()
        val playerId = Uuid.random()
        val tableIds = List(20) { Uuid.random() }

        val results = tableIds.map { tableId -> async { tableId to repository.claim(playerId, tableId) } }.awaitAll()
        val claimedTableId = results.single { it.second }.first

        assertEquals(claimedTableId, repository.getTableId(playerId))
    }

    /** 舊桌子的延遲 release 不得清除玩家後來建立的新歸屬。 */
    @Test
    fun `test release only removes the matching table membership`() = runTest {
        val repository = PlayerMembershipRepositoryImpl()
        val playerId = Uuid.random()
        val oldTableId = Uuid.random()
        val newTableId = Uuid.random()

        assertTrue(repository.claim(playerId, oldTableId))
        repository.release(playerId, oldTableId)
        assertTrue(repository.claim(playerId, newTableId))
        repository.release(playerId, oldTableId)

        assertEquals(newTableId, repository.getTableId(playerId))
        repository.clearAll()
        assertNull(repository.getTableId(playerId))
    }
}
