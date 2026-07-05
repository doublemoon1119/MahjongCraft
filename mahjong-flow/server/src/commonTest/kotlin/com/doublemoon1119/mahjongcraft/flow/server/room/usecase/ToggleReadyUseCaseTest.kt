package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomNotificationService
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
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
     * 測試普通玩家切換準備狀態後，驗證持久化數據與所有觀察者的快照同步。
     */
    @Test
    fun `test toggle ready status and sync to observers`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val notificationService = FakeRoomNotificationService()
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo, notificationService)

        val guestId = UUID.randomUUID()
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId, guestId))
        roomRepo.setRoom(room)

        // 模擬房主正在觀察房間
        snapshotRepo.setSnapshot(hostId, room.toSnapshot(hostId))

        // Act: 客場玩家切換準備
        val toggleResult = useCase(roomId, guestId)
        assertTrue(toggleResult is Outcome.Success, "Expected Success but got $toggleResult")

        // Assert: 檢查持久化資料
        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom)
        assertTrue(updatedRoom.readyPlayerIds.contains(guestId), "Guest player should be in ready set.")

        // Assert: 檢查房主的快照是否同步更新
        val hostSnapshot = snapshotRepo.getSnapshot(roomId, hostId)
        assertNotNull(hostSnapshot)
        assertTrue(hostSnapshot.readyPlayerIds.contains(guestId), "Host snapshot should reflect guest's ready status.")
    }

    /**
     * 驗證點：當玩家切換準備狀態時，房間內所有成員（包含房主與自己）都應收到通知。
     */
    @Test
    fun `test toggle ready notifies all members`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val notificationService = FakeRoomNotificationService()
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo, notificationService)

        val guestId = UUID.randomUUID()
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId, guestId))
        roomRepo.setRoom(room)

        // Act: 客場玩家切換準備（從未準備變更為已準備）
        val toggleResult = useCase(roomId, guestId)
        assertTrue(toggleResult is Outcome.Success, "Expected Success but got $toggleResult")

        // Assert: 房主應收到通知
        val hostReceived = notificationService.getReadyStatus(roomId, hostId, guestId)
        assertEquals(true, hostReceived, "Host should be notified that guest is now ready.")

        // Assert: 發起者本人也應收到通知
        val guestReceived = notificationService.getReadyStatus(roomId, guestId, guestId)
        assertEquals(true, guestReceived, "Guest should be notified of their own ready status change.")
    }

    /**
     * 測試當房主嘗試切換準備狀態時，不應發生任何改變。
     */
    @Test
    fun `test toggle ready does nothing for host`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val notificationService = FakeRoomNotificationService()
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo, notificationService)

        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        // Act
        val toggleResult = useCase(roomId, hostId)
        assertTrue(toggleResult is Outcome.Success, "Expected Success but got $toggleResult")

        // Assert
        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom)
        assertFalse(updatedRoom.readyPlayerIds.contains(hostId), "Host should never be in ready set.")
    }

    /**
     * 測試當非房間內玩家嘗試操作時應拋出異常。
     */
    @Test
    fun `test toggle ready fails when player not in room`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val notificationService = FakeRoomNotificationService()
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo, notificationService)

        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        val strangerId = UUID.randomUUID()

        // Act & Assert
        val result = useCase(roomId, strangerId)
        assertTrue(result is Outcome.Error, "Expected Error but got $result")
    }
}