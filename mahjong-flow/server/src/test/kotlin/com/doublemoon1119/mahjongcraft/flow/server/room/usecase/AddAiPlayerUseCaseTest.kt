package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomNotificationService
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [AddAiPlayerUseCase] 的單元測試類別。
 */
class AddAiPlayerUseCaseTest {

    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val config = FakeMahjongRuleConfig(maxPlayers = 4)

    /**
     * 驗證房主新增 AI 後，AI ID 應同時出現在 playerIds 與 aiPlayerIds 中，且為已準備狀態。
     */
    @Test
    fun `test host adds ai successfully`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = AddAiPlayerUseCase(roomRepo, snapshotRepo, service)

        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        // Act
        val result = useCase(roomId, hostId)
        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val aiId = result.value

        // Assert
        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom)
        assertTrue(updatedRoom.playerIds.contains(aiId), "AI should be in player list.")
        assertTrue(updatedRoom.aiPlayerIds.contains(aiId), "AI should be marked as AI.")
        assertTrue(updatedRoom.readyPlayerIds.contains(aiId), "AI should be ready by default.")

        // 驗證通知
        assertEquals(
            expected = JoinReason.Joined,
            actual = service.getJoinReason(roomId, hostId, aiId)
        )
    }

    /**
     * 驗證當房間人數已達上限時，無法再新增 AI。
     */
    @Test
    fun `test add ai fails when room is full`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = AddAiPlayerUseCase(roomRepo, snapshotRepo, service)

        // 創建一個只有 1 人的房間配置，並填滿它
        val fullConfig = FakeMahjongRuleConfig(maxPlayers = 1)
        val room = Room(id = roomId, hostId = hostId, config = fullConfig, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        val result = useCase(roomId, hostId)
        assertTrue(result is Outcome.Error, "Expected Error but got $result")
    }

    /**
     * 驗證非房主玩家無法新增 AI。
     */
    @Test
    fun `test add ai fails when operator is not host`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = AddAiPlayerUseCase(roomRepo, snapshotRepo, service)

        val guestId = UUID.randomUUID()
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId, guestId))
        roomRepo.setRoom(room)

        val result = useCase(roomId, guestId)
        assertTrue(result is Outcome.Error, "Expected Error but got $result")
    }
}