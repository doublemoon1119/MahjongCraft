package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.room.Room
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.application.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.domain.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

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
        val useCase = CreateRoomUseCase(roomRepo, snapshotRepo)

        // 模擬房主已經是該位置的觀察者
        val roomSnapshot = Room(id = roomId, hostId = hostId, config = config).toSnapshot(hostId)
        snapshotRepo.setSnapshot(hostId, roomSnapshot)

        // Act
        useCase(roomId, hostId, config)

        // Assert: 檢查權威資料
        val savedRoom = roomRepo.getRoom(roomId)
        assertNotNull(savedRoom, "The created room should be persisted.")
        assertEquals(hostId, savedRoom.hostId, "The host ID should match.")

        // Assert: 檢查觀察者快照同步
        val savedSnapshot = snapshotRepo.getSnapshot(roomId, hostId)
        assertNotNull(savedSnapshot, "Observers should receive a room snapshot.")
    }

    /**
     * 測試當房間 ID 已存在時，是否會拋出 [IllegalStateException]。
     *
     * 驗證點：
     * 1. 當 Repository 中已存在相同 ID 的房間時，執行用例應拋出異常。
     */
    @Test
    fun `test create room fails when room id already exists`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = CreateRoomUseCase(roomRepo, snapshotRepo)

        val existingRoom = Room(
            id = roomId,
            hostId = UUID.randomUUID(),
            config = config
        )
        roomRepo.setRoom(existingRoom)

        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException when a room with the same ID already exists."
        ) {
            useCase(roomId, hostId, config)
        }
    }
}