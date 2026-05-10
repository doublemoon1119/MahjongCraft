package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.room.Room
import com.doublemoon1119.mahjongcraft.testing.application.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.domain.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.*

/**
 * [LeaveRoomUseCase] 的單元測試類別。
 */
class LeaveRoomUseCaseTest {

    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試普通玩家離開房間後，房間狀態應正確更新且同步給剩餘玩家。
     */
    @Test
    fun `test guest player leaves room correctly`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = LeaveRoomUseCase(roomRepo, snapshotRepo)

        // Arrange: 建立一個 4 人房間，其中一名客場玩家已準備
        val guestIds = List(3) { UUID.randomUUID() }
        val leavingPlayerId = guestIds[0]
        val allPlayerIds = (guestIds + hostId).toSet()
        val room = Room(
            id = roomId,
            hostId = hostId,
            config = config,
            playerIds = allPlayerIds,
            readyPlayerIds = setOf(leavingPlayerId)
        )
        roomRepo.setRoom(room)

        // Act: 其中一名準備好的客場玩家離開
        useCase(roomId, leavingPlayerId)

        // Assert: 檢查人數與準備名單
        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom)
        assertEquals(3, updatedRoom.playerIds.size, "Room should have 3 players remaining.")
        assertFalse(
            updatedRoom.playerIds.contains(leavingPlayerId),
            "The leaving player should be removed from playerIds."
        )
        assertFalse(
            updatedRoom.readyPlayerIds.contains(leavingPlayerId),
            "The leaving player should be removed from readyPlayerIds."
        )

        // Assert: 檢查剩餘玩家是否收到同步
        assertNotNull(snapshotRepo.getSnapshot(roomId, hostId), "Host should receive updated snapshot.")
    }

    /**
     * 測試當房主離開時，房間應從倉庫中移除（解散）。
     */
    @Test
    fun `test room is removed when host leaves`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = LeaveRoomUseCase(roomRepo, snapshotRepo)

        // Arrange
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        // Act: 房主離開
        useCase(roomId, hostId)

        // Assert: 房間應不存在
        assertNull(roomRepo.getRoom(roomId), "The room should be dissolved and removed when the host leaves.")
    }
}