package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.room.Room
import com.doublemoon1119.mahjongcraft.testing.application.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.domain.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [SyncRoomSnapshotUseCase] 的單元測試類別。
 */
class SyncRoomSnapshotUseCaseTest {

    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試玩家請求同步時，是否能正確收到針對該玩家身分生成的快照。
     *
     * 驗證點：
     * 1. 快照已存入 [FakeRoomSnapshotRepository] 且對象為請求者。
     * 2. 快照內容反映了請求者的身分狀態。
     */
    @Test
    fun `test sync room snapshot for host correctly`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = SyncRoomSnapshotUseCase(roomRepo, snapshotRepo)

        // 預先準備一個房間
        val room = Room(id = roomId, hostId = hostId, config = config)
        roomRepo.setRoom(room)

        // 模擬房主請求同步
        useCase(roomId, hostId)

        val snapshot = snapshotRepo.getSnapshot(roomId, hostId)
        assertNotNull(snapshot, "A snapshot should be generated for the host.")
        assertTrue(snapshot.isHost, "When the host synchronizes, the isHost flag in the snapshot should be true.")
    }
}