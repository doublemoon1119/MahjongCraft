package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.application.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.room.Room
import com.doublemoon1119.mahjongcraft.testing.application.common.room.repository.FakeRoomSnapshotRepository
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
     * 測試房主成功將準備好的玩家剔除。
     */
    @Test
    fun `test host kicks player successfully`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = KickPlayerUseCase(roomRepo, snapshotRepo)

        val targetId = UUID.randomUUID()
        val room = Room(
            id = roomId,
            hostId = hostId,
            config = config,
            playerIds = setOf(hostId, targetId),
            readyPlayerIds = setOf(targetId)
        )
        roomRepo.setRoom(room)

        // Act: 房主執行剔除
        useCase(roomId, hostId, targetId)

        // Assert: 檢查房間狀態
        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom)
        assertFalse(updatedRoom.playerIds.contains(targetId), "Target player should be removed from playerIds.")
        assertFalse(
            updatedRoom.readyPlayerIds.contains(targetId),
            "Target player should be removed from readyPlayerIds."
        )

        // Assert: 房主應收到更新後的快照
        assertNotNull(snapshotRepo.getSnapshot(roomId, hostId), "Host should receive an updated snapshot.")
    }

    /**
     * 測試當非房主嘗試剔除玩家時，應拋出異常。
     */
    @Test
    fun `test kick fails when operator is not host`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = KickPlayerUseCase(roomRepo, snapshotRepo)

        val guestId1 = UUID.randomUUID()
        val guestId2 = UUID.randomUUID()
        val room = Room(
            id = roomId,
            hostId = hostId,
            config = config,
            playerIds = setOf(hostId, guestId1, guestId2)
        )
        roomRepo.setRoom(room)

        // Act & Assert: 普通玩家嘗試踢人
        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException when a non-host player attempts to kick someone.",
            block = { useCase(roomId, guestId1, guestId2) }
        )
    }

    /**
     * 測試房主嘗試剔除自己時應拋出異常。
     */
    @Test
    fun `test host cannot kick themselves`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = KickPlayerUseCase(roomRepo, snapshotRepo)

        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException when the host attempts to kick themselves.",
            block = { useCase(roomId, hostId, hostId) }
        )
    }

    /**
     * 測試玩家被剔除時，應記錄原因為 Kicked。
     */
    @Test
    fun `test kicked player receives kicked reason`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = KickPlayerUseCase(roomRepo, snapshotRepo)

        val targetId = UUID.randomUUID()
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId, targetId))
        roomRepo.setRoom(room)

        // Act
        useCase(roomId, hostId, targetId)

        // Assert
        assertEquals(
            LeaveReason.Kicked,
            snapshotRepo.getLastLeaveReason(roomId, targetId),
            "The kicked player should receive Kicked as the leave reason."
        )
    }
}