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
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo)

        val guestId = UUID.randomUUID()
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId, guestId))
        roomRepo.setRoom(room)

        // 模擬房主與客場玩家都在觀察
        snapshotRepo.setSnapshot(hostId, room.copy().toSnapshot(hostId))
        snapshotRepo.setSnapshot(guestId, room.copy().toSnapshot(guestId))

        // Act: 玩家切換為準備
        useCase(roomId, guestId)

        // Assert: 檢查權威資料
        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom)
        assertTrue(updatedRoom.readyPlayerIds.contains(guestId), "Player should be in ready state.")

        // Assert: 檢查房主收到的快照是否更新
        val hostSnapshot = snapshotRepo.getSnapshot(roomId, hostId)
        assertNotNull(hostSnapshot)
        assertTrue(hostSnapshot.readyPlayerIds.contains(guestId), "Host snapshot should reflect guest's ready status.")
    }

    /**
     * 測試當房主嘗試切換準備狀態時，不應發生任何改變。
     */
    @Test
    fun `test toggle ready does nothing for host`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo)

        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        // Act
        useCase(roomId, hostId)

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
        val useCase = ToggleReadyUseCase(roomRepo, snapshotRepo)

        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId))
        roomRepo.setRoom(room)

        val intruderId = UUID.randomUUID()

        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException if the player is not a member of the room."
        ) {
            useCase(roomId, intruderId)
        }
    }
}