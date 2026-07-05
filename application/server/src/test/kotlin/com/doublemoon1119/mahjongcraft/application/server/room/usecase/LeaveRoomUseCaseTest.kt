package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.common.result.Outcome
import com.doublemoon1119.mahjongcraft.application.common.room.model.LeaveReason
import com.doublemoon1119.mahjongcraft.application.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.application.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.room.Room
import com.doublemoon1119.mahjongcraft.domain.room.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.application.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.application.common.room.service.FakeRoomNotificationService
import com.doublemoon1119.mahjongcraft.testing.domain.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.*

/**
 * [LeaveRoomUseCase] 的單元測試類別。
 */
class LeaveRoomUseCaseTest {

    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試普通玩家離開房間，驗證房間狀態更新與 Voluntary 通知。
     */
    @Test
    fun `test guest player leaves with voluntary reason`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = LeaveRoomUseCase(roomRepo, snapshotRepo, service)

        val guestId = UUID.randomUUID()
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId, guestId))
        roomRepo.setRoom(room)

        // 模擬兩位玩家都在觀察
        snapshotRepo.setSnapshot(hostId, room.toSnapshot(hostId))
        snapshotRepo.setSnapshot(guestId, room.toSnapshot(guestId))

        // Act
        val result = useCase(roomId, guestId)
        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        // Assert: 檢查通知
        assertEquals(
            expected = LeaveReason.Voluntary,
            actual = service.getLeaveReason(roomId, guestId, guestId),
            message = "The leaving guest should receive Voluntary reason."
        )

        // Assert: 檢查快照更新（房主應看到玩家已離開）
        val hostSnapshot = snapshotRepo.getSnapshot(roomId, hostId)
        assertNotNull(hostSnapshot)
        assertFalse(hostSnapshot.playerIds.contains(guestId), "Host snapshot should reflect guest left.")
    }

    /**
     * 測試房主離開時，驗證房間解散且所有觀察者收到 Dissolved 通知。
     */
    @Test
    fun `test all observers get dissolved reason when host leaves`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = LeaveRoomUseCase(roomRepo, snapshotRepo, service)

        val guestId = UUID.randomUUID()
        val room = Room(id = roomId, hostId = hostId, config = config, playerIds = setOf(hostId, guestId))
        roomRepo.setRoom(room)

        // 模擬房主與客場玩家都在觀察該房間
        snapshotRepo.setSnapshot(hostId, room.toSnapshot(hostId))
        snapshotRepo.setSnapshot(guestId, room.toSnapshot(guestId))

        // Act: 房主解散房間
        val dissolveResult = useCase(roomId, hostId)
        assertTrue(dissolveResult is Outcome.Success, "Expected Success but got $dissolveResult")

        // Assert: 檢查解散通知
        assertEquals(
            expected = LeaveReason.Dissolved,
            actual = service.getLeaveReason(roomId, hostId, hostId),
            message = "Host should be notified of dissolution."
        )
        assertEquals(
            expected = LeaveReason.Dissolved,
            actual = service.getLeaveReason(roomId, guestId, guestId),
            message = "Guest should be notified of dissolution."
        )

        // Assert: 檢查快照是否已移除
        assertNull(snapshotRepo.getSnapshot(roomId, hostId), "Host snapshot should be removed.")
        assertNull(snapshotRepo.getSnapshot(roomId, guestId), "Guest snapshot should be removed.")
    }

    /**
     * 測試對不存在的房間執行離開操作時，應回傳 [RoomError.RoomNotFound]。
     */
    @Test
    fun `test leave room fails when room does not exist`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val service = FakeRoomNotificationService()
        val useCase = LeaveRoomUseCase(roomRepo, snapshotRepo, service)

        val result = useCase(roomId, hostId)
        assertTrue(result is Outcome.Error, "Expected Error but got $result")
        assertEquals(RoomError.RoomNotFound(roomId), (result as Outcome.Error).error)
    }
}