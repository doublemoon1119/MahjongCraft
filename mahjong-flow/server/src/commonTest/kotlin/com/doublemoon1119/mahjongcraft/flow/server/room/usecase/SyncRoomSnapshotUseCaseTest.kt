package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [SyncRoomSnapshotUseCase] 的單元測試類別。
 */
class SyncRoomSnapshotUseCaseTest {

    private val roomId: Uuid = Uuid.random()
    private val hostId: Uuid = Uuid.random()
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試玩家請求同步時，應正確生成針對該玩家身分的快照。
     */
    @Test
    fun `test sync room snapshot for host correctly`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = SyncRoomSnapshotUseCase(roomRepo, snapshotRepo)

        val room = Room(id = roomId, hostId = hostId, config = config)
        roomRepo.setRoom(room)

        // Act
        val result = useCase(roomId, hostId)
        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        // Assert
        val snapshot = snapshotRepo.getSnapshot(roomId, hostId)
        assertNotNull(snapshot, "A snapshot should be generated for the host.")
        assertTrue(snapshot.isHost, "The host's snapshot should have isHost set to true.")
    }

    /**
     * 測試當房間不存在時，應回傳 [RoomError.RoomNotFound]。
     */
    @Test
    fun `test sync room snapshot fails when room not exists`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = SyncRoomSnapshotUseCase(roomRepo, snapshotRepo)

        // Act
        val result = useCase(roomId, hostId)

        // Assert
        assertTrue(result is Outcome.Error, "Expected Error but got $result")
        assertEquals(RoomError.RoomNotFound(roomId), (result as Outcome.Error).error)
    }
}