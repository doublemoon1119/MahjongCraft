package com.doublemoon1119.mahjongcraft.flow.common.room.repository

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證房間快照的 guarded removal 會在來源過期時保留資料。 */
class RoomSnapshotRepositoryImplTest {
    /** 過期條件不得移除現有快照，通過條件才移除。 */
    @Test
    fun `guarded removal preserves snapshot when source is stale`() = runTest {
        val repository = RoomSnapshotRepositoryImpl()
        val roomId = Uuid.random()
        val observerId = Uuid.random()
        repository.setSnapshot(observerId, snapshot(roomId, observerId))

        assertFalse(repository.removeSnapshotIf(roomId, observerId) { false })
        assertEquals(setOf(observerId), repository.getAllObservers(roomId))
        assertTrue(repository.removeSnapshotIf(roomId, observerId) { true })
        assertEquals(emptySet(), repository.getAllObservers(roomId))
    }

    /** 取得的接收者集合不會跟著 repository 後續變動，清理可安全保存它。 */
    @Test
    fun `captured observers remain stable after snapshot removal`() = runTest {
        val repository = RoomSnapshotRepositoryImpl()
        val roomId = Uuid.random()
        val observerId = Uuid.random()
        repository.setSnapshot(observerId, snapshot(roomId, observerId))
        val captured = repository.getAllObservers(roomId)

        repository.removeSnapshot(roomId, observerId)

        assertEquals(setOf(observerId), captured)
        assertEquals(emptySet(), repository.getAllObservers(roomId))
    }

    /**
     * 建立內容最小但完整的房間觀察者快照。
     *
     * @param roomId 快照所屬的房間。
     * @param observerId 房間主持人及快照觀察者。
     * @return 可供 repository 保存的房間快照。
     */
    private fun snapshot(roomId: Uuid, observerId: Uuid) = RoomSnapshot(
        id = roomId,
        hostId = observerId,
        gameConfig = GameConfig(FakeMahjongRuleConfig()),
        playerIds = listOf(observerId),
        readyPlayerIds = emptyList(),
        aiPlayerIds = emptyList(),
        canStart = false,
        isHost = true,
        isInRoom = true,
    )
}
