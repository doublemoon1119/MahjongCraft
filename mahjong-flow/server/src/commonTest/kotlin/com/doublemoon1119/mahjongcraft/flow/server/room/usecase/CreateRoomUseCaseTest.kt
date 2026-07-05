package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.JoinReason
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
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
 * [CreateRoomUseCase] 的單元測試類別。
 *
 * 驗證創建房間的業務邏輯，包含資料持久化與觀察者快照的同步行為。
 */
class CreateRoomUseCaseTest {

    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試創建房間是否能正確持久化資料，並向包含房主在內的觀察者同步快照。
     */
    @Test
    fun `test create room and sync snapshots to observers`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = CreateRoomUseCase(roomRepo, snapshotRepo, service)

        // 模擬房主已經是該位置的觀察者
        val roomSnapshot = Room(id = roomId, hostId = hostId, config = config).toSnapshot(hostId)
        snapshotRepo.setSnapshot(hostId, roomSnapshot)

        // Act
        val result = useCase(roomId, hostId, config)
        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        // Assert: 檢查權威資料
        val savedRoom = roomRepo.getRoom(roomId)
        assertNotNull(savedRoom, "The created room should be persisted.")
        assertEquals(hostId, savedRoom.hostId, "The host ID should match.")

        // Assert: 檢查觀察者快照同步
        val savedSnapshot = snapshotRepo.getSnapshot(roomId, hostId)
        assertNotNull(savedSnapshot, "Observers should receive a room snapshot.")
    }

    /**
     * 測試當房間 ID 已存在時，應回傳 [RoomError.RoomAlreadyExists]。
     */
    @Test
    fun `test create room fails when room id already exists`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = CreateRoomUseCase(roomRepo, snapshotRepo, service)

        val existingRoom = Room(
            id = roomId,
            hostId = UUID.randomUUID(),
            config = config
        )
        roomRepo.setRoom(existingRoom)

        val result = useCase(roomId, hostId, config)
        assertTrue(result is Outcome.Error)
        assertEquals(RoomError.RoomAlreadyExists(roomId), (result as Outcome.Error).error)
    }

    /**
     * 測試創建房間時，房主應收到 Created 的加入通知。
     */
    @Test
    fun `test create room notifies host with created reason`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = CreateRoomUseCase(roomRepo, snapshotRepo, service)

        // Act
        val result = useCase(roomId, hostId, config)
        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        // Assert: 驗證持久化
        assertNotNull(roomRepo.getRoom(roomId))

        // Assert: 驗證加入原因
        assertEquals(
            expected = JoinReason.Created,
            actual = service.getJoinReason(roomId, hostId, hostId),
            message = "Host should be notified with Created reason when opening a room."
        )
    }
}