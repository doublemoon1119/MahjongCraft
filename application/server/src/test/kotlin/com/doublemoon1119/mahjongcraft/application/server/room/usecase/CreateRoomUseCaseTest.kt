package com.doublemoon1119.mahjongcraft.application.server.room.usecase

import com.doublemoon1119.mahjongcraft.application.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.room.Room
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
 * 驗證創建房間的業務邏輯，包含資料持久化與初始快照的同步行為。
 */
class CreateRoomUseCaseTest {

    /** 測試用的房間唯一識別碼。 */
    private val roomId: UUID = UUID.randomUUID()

    /** 測試用的房主玩家 ID。 */
    private val hostId: UUID = UUID.randomUUID()

    /** 測試用的麻將規則配置。 */
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試創建房間是否能正確持久化資料並產生快照。
     *
     * 驗證點：
     * 1. 權威房間資料已存入 [FakeRoomRepository]。
     * 2. 初始快照已針對房主存入 [FakeRoomSnapshotRepository]。
     */
    @Test
    fun `test create room and persist correctly`() = runTest {
        val roomRepo = FakeRoomRepository()
        val snapshotRepo = FakeRoomSnapshotRepository()
        val useCase = CreateRoomUseCase(roomRepo, snapshotRepo)

        useCase(roomId, hostId, config)

        val savedRoom = roomRepo.getRoom(roomId)
        assertNotNull(savedRoom, "The created room should be persisted in the repository.")
        assertEquals(hostId, savedRoom.hostId, "The host ID in the persisted room should match the creator.")

        val savedSnapshot = snapshotRepo.getSnapshot(roomId, hostId)
        assertNotNull(savedSnapshot, "A room snapshot should be generated and synchronized for the host.")
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

        // 預先存入一個具有相同 ID 的房間
        val existingRoom = Room(
            id = roomId,
            hostId = UUID.randomUUID(),
            config = config
        )
        roomRepo.setRoom(existingRoom)

        // 驗證執行時拋出 IllegalStateException
        assertFailsWith<IllegalStateException>(
            message = "Should throw IllegalStateException when a room with the same ID already exists."
        ) {
            useCase(roomId, hostId, config)
        }
    }
}