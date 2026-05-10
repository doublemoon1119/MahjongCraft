package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.room.Room
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.application.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.domain.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.*

/**
 * [JoinRoomUseCase] 的單元測試類別。
 *
 * 驗證玩家加入房間的各種情境，確保數據持久化與觀察者快照同步正確執行。
 */
class JoinRoomUseCaseTest {

    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val otherPlayerId: UUID = UUID.randomUUID()
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試玩家成功加入房間，並驗證所有觀察者是否都收到更新快照。
     */
    @Test
    fun `test join room successfully and sync snapshots`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = JoinRoomUseCase(roomRepo, snapshotRepo)

        val initialRoom = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(initialRoom)

        // 模擬房主與新玩家都已經是該位置的觀察者
        snapshotRepo.setSnapshot(hostId, initialRoom.toSnapshot(hostId))
        snapshotRepo.setSnapshot(otherPlayerId, initialRoom.toSnapshot(otherPlayerId))

        // Act
        useCase(roomId, otherPlayerId)

        // Assert: 檢查權威資料
        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom)
        assertTrue(updatedRoom.playerIds.contains(otherPlayerId))

        // Assert: 檢查快照同步
        val hostSnapshot = snapshotRepo.getSnapshot(roomId, hostId)
        val guestSnapshot = snapshotRepo.getSnapshot(roomId, otherPlayerId)

        assertNotNull(hostSnapshot, "Host should receive an updated snapshot.")
        assertNotNull(guestSnapshot, "New player should receive their updated snapshot.")
        assertTrue(hostSnapshot.playerIds.contains(otherPlayerId), "Snapshot should reflect new member.")
    }

    /**
     * 測試當房間已滿時，玩家加入應拋出 IllegalStateException。
     */
    @Test
    fun `test join room fails when room is full`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = JoinRoomUseCase(roomRepo, snapshotRepo)

        val fullPlayerIds = (1..4).map { UUID.randomUUID() }.toSet()
        val fullRoom = Room(id = roomId, hostId = fullPlayerIds.first(), config = config, playerIds = fullPlayerIds)
        roomRepo.setRoom(fullRoom)

        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException when trying to join a full room."
        ) {
            useCase(roomId, otherPlayerId)
        }
    }

    /**
     * 測試當玩家已在房間內時，重複加入應拋出異常。
     */
    @Test
    fun `test join room fails when player already in room`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = JoinRoomUseCase(roomRepo, snapshotRepo)

        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException when the player is already in the room."
        ) {
            useCase(roomId, hostId)
        }
    }
}