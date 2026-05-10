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
 * [ToggleReadyUseCase] 的單元測試類別。
 */
class ToggleReadyUseCaseTest {

    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試普通玩家切換準備狀態是否正確。
     */
    @Test
    fun `test toggle ready status for guest player correctly`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo)

        val guestId = UUID.randomUUID()
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId, guestId))
        roomRepo.setRoom(room)

        // Act: 玩家切換至準備
        useCase(roomId, guestId)
        val readyRoom = roomRepo.getRoom(roomId)
        assertNotNull(readyRoom)
        assertTrue(readyRoom.readyPlayerIds.contains(guestId), "Guest player should be in ready state.")

        // Act: 玩家再次切換（取消準備）
        useCase(roomId, guestId)
        val unreadyRoom = roomRepo.getRoom(roomId)
        assertNotNull(unreadyRoom)
        assertFalse(unreadyRoom.readyPlayerIds.contains(guestId), "Guest player should be removed from ready state.")
    }

    /**
     * 測試房主嘗試切換準備狀態時，不應有任何變更。
     */
    @Test
    fun `test toggle ready for host should do nothing`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo)

        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        // Act: 房主嘗試切換準備
        useCase(roomId, hostId)

        val finalRoom = roomRepo.getRoom(roomId)
        assertNotNull(finalRoom)
        assertFalse(finalRoom.readyPlayerIds.contains(hostId), "Host should never be in the readyPlayerIds set.")
    }

    /**
     * 測試當所有非房主玩家（共 3 名）都準備時，房間狀態應更新為可以開始。
     *
     * 驗證點：
     * 1. 只有部分非房主玩家準備時，canStart 應為 false。
     * 2. 當所有非房主玩家皆完成準備，canStart 應為 true。
     */
    @Test
    fun `test canStart becomes true when all guest players are ready`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo)

        // Arrange: 建立包含 1 名房主與 3 名客場玩家的房間（共 4 人）
        val guestIds = List(3) { UUID.randomUUID() }
        val allPlayerIds = (guestIds + hostId).toSet()
        val room = Room(
            id = roomId,
            hostId = hostId,
            config = config,
            playerIds = allPlayerIds
        )
        roomRepo.setRoom(room)

        // Act & Assert: 前兩名客場玩家準備，此時人數尚未滿足「除房主外全體準備」
        useCase(roomId, guestIds[0])
        useCase(roomId, guestIds[1])

        val intermediateRoom = roomRepo.getRoom(roomId)
        assertNotNull(intermediateRoom)
        assertFalse(
            intermediateRoom.canStart,
            "Room should not be able to start when some guest players are still not ready."
        )

        // Act: 最後一名客場玩家準備
        useCase(roomId, guestIds[2])

        // Assert: 檢查領域模型的 canStart 邏輯
        val finalRoom = roomRepo.getRoom(roomId)
        assertNotNull(finalRoom)
        assertTrue(
            finalRoom.canStart,
            "Room should be ready to start when all 3 guest players are ready, even if the host is not in the ready set."
        )
    }

    /**
     * 測試當非房間內玩家嘗試切換狀態時應拋出異常。
     */
    @Test
    fun `test toggle ready fails when player not in room`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo)

        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        val strangerId = UUID.randomUUID()
        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException when player is not in the room.",
            block = { useCase(roomId, strangerId) }
        )
    }
}