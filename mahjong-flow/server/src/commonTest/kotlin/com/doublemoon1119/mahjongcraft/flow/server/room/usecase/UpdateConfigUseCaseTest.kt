package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
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

    /** 受測房間的識別碼。 */
    private val roomId: Uuid = Uuid.random()

    /** 房主的玩家識別碼。 */
    private val hostId: Uuid = Uuid.random()

    /** 受測房間目前的規則設定。 */
    private val initialConfig: MahjongRuleConfig = FakeMahjongRuleConfig()

    /** 更新後的規則設定。 */
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
            playerIds = listOf(hostId, guestId),
        )
        roomRepo.setRoom(room)

        // 模擬房主與客場玩家正在觀察房間
        snapshotRepo.setSnapshot(hostId, room.toSnapshot(hostId))
        snapshotRepo.setSnapshot(guestId, room.toSnapshot(guestId))

        // Act: 房主發起配置更新
        val result = useCase(roomId, hostId, GameConfig(newConfig))
        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        assertEquals(newConfig, result.value.ruleConfig, "Should return the config that was applied.")

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
     * 驗證非房主嘗試修改設定時回傳錯誤結果，且房間設定維持不變。
     */
    @Test
    fun `test update config fails when operator is not host`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val notificationService = FakeRoomEventPublisher()
        val useCase = UpdateConfigUseCase(roomRepo, snapshotRepo, notificationService)

        val guestId = Uuid.random()
        val room = Room(id = roomId, hostId = hostId, gameConfig = GameConfig(initialConfig), playerIds = listOf(hostId, guestId))
        roomRepo.setRoom(room)

        // Act & Assert: 客場玩家嘗試修改配置
        val result = useCase(roomId, guestId, GameConfig(newConfig))
        assertTrue(result is Outcome.Error, "Expected Error but got $result")

        // 驗證配置維持原樣
        val roomInRepo = roomRepo.getRoom(roomId)
        assertEquals(initialConfig, roomInRepo?.gameConfig?.ruleConfig, "Config should remain unchanged after failed update.")
    }

    /**
     * 驗證更新不存在的房間時回傳錯誤結果。
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

    /** 變更規則設定時清除人類玩家的準備狀態並保留 AI 玩家狀態。 */
    @Test
    fun `changed config clears human ready state but preserves AI ready state`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = UpdateConfigUseCase(roomRepo, snapshotRepo, FakeRoomEventPublisher())
        val humanId = Uuid.random()
        val aiId = Uuid.random()
        roomRepo.setRoom(
            Room(
                id = roomId,
                hostId = hostId,
                gameConfig = GameConfig(initialConfig),
                playerIds = listOf(hostId, humanId, aiId),
                readyPlayerIds = listOf(humanId, aiId),
                aiPlayerStrategyKeys = mapOf(aiId to "mahjongcraft:default"),
            ),
        )

        assertTrue(useCase(roomId, hostId, GameConfig(FakeMahjongRuleConfig(minimumWinConstraint = 2))) is Outcome.Success)

        assertEquals(listOf(aiId), roomRepo.getRoom(roomId)?.readyPlayerIds)
    }

    /** 新設定的人數上限低於現有人數時，整筆更新應被拒絕。 */
    @Test
    fun `config with smaller maximum player count is rejected atomically`() = runTest {
        val roomRepo = FakeRoomRepository()
        val useCase = UpdateConfigUseCase(roomRepo, FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
        val original = Room(
            id = roomId,
            hostId = hostId,
            gameConfig = GameConfig(initialConfig),
            playerIds = listOf(hostId, Uuid.random(), Uuid.random()),
        )
        roomRepo.setRoom(original)

        val result = useCase(roomId, hostId, GameConfig(FakeMahjongRuleConfig(minPlayers = 2, maxPlayers = 2)))

        assertTrue(result is Outcome.Error)
        assertEquals(original.gameConfig, roomRepo.getRoom(roomId)?.gameConfig)
    }

    /** 停用旁觀時將旁觀者手牌可見度一併限制為隱藏。 */
    @Test
    fun `disabling spectating also hides spectator hands`() = runTest {
        val roomRepo = FakeRoomRepository()
        val useCase = UpdateConfigUseCase(roomRepo, FakeRoomSnapshotRepository(), FakeRoomEventPublisher())
        roomRepo.setRoom(Room(roomId, hostId, GameConfig(initialConfig), listOf(hostId)))
        val contradictory = GameConfig(
            newConfig,
            GameFlowConfig(
                spectatingPolicy = SpectatingPolicy.DISABLED,
                spectatorHandVisibility = SpectatorHandVisibility.REVEALED,
            ),
        )

        val result = useCase(roomId, hostId, contradictory)

        assertTrue(result is Outcome.Success)
        assertEquals(SpectatorHandVisibility.HIDDEN, result.value.flowConfig.spectatorHandVisibility)
        assertEquals(SpectatorHandVisibility.HIDDEN, roomRepo.getRoom(roomId)?.gameConfig?.flowConfig?.spectatorHandVisibility)
    }
}
