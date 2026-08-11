package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [UpdateConfigUseCase] 的單元測試類別。
 *
 * 驗證房間配置更新的業務邏輯，包含權限校驗、快照同步以及異步通知的發送。
 */
class UpdateConfigUseCaseTest {

    private val roomId: Uuid = Uuid.random()
    private val hostId: Uuid = Uuid.random()
    private val initialConfig: MahjongRuleConfig = FakeMahjongRuleConfig()
    private val newConfig: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試房主成功更新配置，並驗證持久化數據、快照同步以及所有成員收到的通知。
     */
    @Test
    fun `test host updates config successfully`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val notificationService = FakeRoomEventPublisher()
        val useCase = UpdateConfigUseCase(roomRepo, snapshotRepo, notificationService)

        val guestId = Uuid.random()
        val room = Room(
            id = roomId,
            hostId = hostId,
            gameConfig = GameConfig(initialConfig),
            playerIds = setOf(hostId, guestId),
        )
        roomRepo.setRoom(room)

        // 模擬房主與客場玩家正在觀察房間
        snapshotRepo.setSnapshot(hostId, room.toSnapshot(hostId))
        snapshotRepo.setSnapshot(guestId, room.toSnapshot(guestId))

        // Act: 房主發起配置更新
        val result = useCase(roomId, hostId, GameConfig(newConfig))
        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        // Assert: 檢查持久化數據是否更新
        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom)
        assertEquals(newConfig, updatedRoom.gameConfig.ruleConfig, "Room config in repository should be updated.")

        // Assert: 檢查快照是否同步更新（以客場玩家為例）
        val guestSnapshot = snapshotRepo.getSnapshot(roomId, guestId)
        assertNotNull(guestSnapshot)
        assertEquals(newConfig, guestSnapshot.gameConfig.ruleConfig, "Guest snapshot should reflect the new config.")

        // Assert: 檢查房間成員是否都收到配置變更通知
        assertEquals(
            expected = newConfig,
            actual = notificationService.getConfigChangedNotification(roomId, hostId),
            message = "Host should receive config changed notification.",
        )
        assertEquals(
            expected = newConfig,
            actual = notificationService.getConfigChangedNotification(roomId, guestId),
            message = "Guest should receive config changed notification.",
        )
    }

    /**
     * 測試當非房主玩家試圖修改配置時，應拋出異常且數據不應變動。
     */
    @Test
    fun `test update config fails when operator is not host`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val notificationService = FakeRoomEventPublisher()
        val useCase = UpdateConfigUseCase(roomRepo, snapshotRepo, notificationService)

        val guestId = Uuid.random()
        val room = Room(id = roomId, hostId = hostId, gameConfig = GameConfig(initialConfig), playerIds = setOf(hostId, guestId))
        roomRepo.setRoom(room)

        // Act & Assert: 客場玩家嘗試修改配置
        val result = useCase(roomId, guestId, GameConfig(newConfig))
        assertTrue(result is Outcome.Error, "Expected Error but got $result")

        // 驗證配置維持原樣
        val roomInRepo = roomRepo.getRoom(roomId)
        assertEquals(initialConfig, roomInRepo?.gameConfig?.ruleConfig, "Config should remain unchanged after failed update.")
    }

    /**
     * 測試針對不存在的房間 ID 進行操作時應拋出異常。
     */
    @Test
    fun `test update config fails when room does not exist`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val notificationService = FakeRoomEventPublisher()
        val useCase = UpdateConfigUseCase(roomRepo, snapshotRepo, notificationService)

        // Act & Assert
        val result = useCase(roomId, hostId, GameConfig(newConfig))
        assertTrue(result is Outcome.Error, "Expected Error but got $result")
    }
}
