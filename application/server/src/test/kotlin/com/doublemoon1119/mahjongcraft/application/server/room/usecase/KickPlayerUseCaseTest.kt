package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.application.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.room.Room
import com.doublemoon1119.mahjongcraft.testing.application.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.application.common.room.service.FakeRoomNotificationService
import com.doublemoon1119.mahjongcraft.testing.domain.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.*

/**
 * [KickPlayerUseCase] 的單元測試類別。
 */
class KickPlayerUseCaseTest {

    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試房主成功將玩家剔除，並驗證通知服務是否收到 Kicked 原因。
     */
    @Test
    fun `test host kicks player successfully`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = KickPlayerUseCase(roomRepo, snapshotRepo, service)

        val targetId = UUID.randomUUID()
        val room = Room(
            id = roomId,
            hostId = hostId,
            config = config,
            playerIds = setOf(hostId, targetId),
            readyPlayerIds = setOf(targetId)
        )
        roomRepo.setRoom(room)

        // Act
        useCase(roomId, hostId, targetId)

        // Assert: 檢查持久化資料
        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom)
        assertFalse(updatedRoom.playerIds.contains(targetId), "Target should be removed from playerIds.")
        assertFalse(updatedRoom.readyPlayerIds.contains(targetId), "Target should be removed from readyPlayerIds.")

        // Assert: 檢查通知服務
        assertEquals(
            expected = LeaveReason.Kicked,
            actual = service.getLeaveReason(roomId, targetId, targetId),
            message = "The kicked player should receive Kicked reason via notification service."
        )
    }

    /**
     * 測試當房主試圖剔除自己時應拋出異常。
     */
    @Test
    fun `test host cannot kick themselves`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = KickPlayerUseCase(roomRepo, snapshotRepo, service)

        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException when host tries to kick themselves."
        ) {
            useCase(roomId, hostId, hostId)
        }
    }

    /**
     * 測試非房主玩家試圖剔除他人時應拋出異常。
     */
    @Test
    fun `test non host cannot kick player`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = KickPlayerUseCase(roomRepo, snapshotRepo, service)

        val guestId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId, guestId, targetId))
        roomRepo.setRoom(room)

        assertFailsWith<IllegalStateException>(
            message = "Only the host should have permission to kick players."
        ) {
            useCase(roomId, guestId, targetId)
        }
    }
}