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
 * [JoinRoomUseCase] 的單元測試類別。
 *
 * 驗證玩家加入房間的各種情境，包含成功加入、重複加入以及人數已滿的錯誤處理。
 */
class JoinRoomUseCaseTest {

    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val otherPlayerId: UUID = UUID.randomUUID()
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試玩家成功加入房間。
     *
     * 驗證點：
     * 1. 玩家 ID 已被加進權威 [Room] 模型。
     * 2. 房間內的所有成員都收到了更新後的快照。
     */
    @Test
    fun `test join room successfully`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = JoinRoomUseCase(roomRepo, snapshotRepo)

        // Arrange: 建立一個只有房主的房間
        val initialRoom = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(initialRoom)

        // Act: 其他玩家加入
        useCase(roomId, otherPlayerId)

        // Assert: 檢查人數與成員清單
        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom, "Room should still exist after joining.")
        assertTrue(updatedRoom.playerIds.contains(otherPlayerId), "New player should be added to playerIds.")
        assertEquals(2, updatedRoom.playerIds.size, "Room should contain exactly 2 players.")

        // Assert: 檢查快照同步
        assertNotNull(snapshotRepo.getSnapshot(roomId, hostId), "Host should receive an updated snapshot.")
        assertNotNull(
            snapshotRepo.getSnapshot(roomId, otherPlayerId),
            "New player should receive their first snapshot."
        )
    }

    /**
     * 測試當房間已滿時，玩家加入應拋出異常。
     */
    @Test
    fun `test join room fails when room is full`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = JoinRoomUseCase(roomRepo, snapshotRepo)

        // Arrange: 模擬一個已滿的房間 (FakeMahjongRuleConfig 最大人數為 4)
        val fullPlayerIds = (1..4).map { UUID.randomUUID() }.toSet()
        val fullRoom = Room(id = roomId, hostId = fullPlayerIds.first(), config = config, playerIds = fullPlayerIds)
        roomRepo.setRoom(fullRoom)

        // Act & Assert
        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException when trying to join a full room.",
            block = { useCase(roomId, otherPlayerId) }
        )
    }

    /**
     * 測試當玩家已在房間內時，重複加入應拋出異常。
     */
    @Test
    fun `test join room fails when player is already in room`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = JoinRoomUseCase(roomRepo, snapshotRepo)

        // Arrange
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        // Act & Assert: 房主嘗試再次加入
        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException when the player is already in the room.",
            block = { useCase(roomId, hostId) }
        )
    }
}