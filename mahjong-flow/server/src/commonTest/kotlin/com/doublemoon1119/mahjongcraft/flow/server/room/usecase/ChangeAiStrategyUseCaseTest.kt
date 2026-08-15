package com.doublemoon1119.mahjongcraft.flow.server.room.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.FakeRoomRepository
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [ChangeAiStrategyUseCase] 的單元測試類別。
 */
class ChangeAiStrategyUseCaseTest {

    private val roomId: Uuid = Uuid.random()
    private val hostId: Uuid = Uuid.random()
    private val config: MahjongRuleConfig = FakeMahjongRuleConfig()

    /**
     * 測試房主成功替房間內的 AI 更換策略。
     */
    @Test
    fun `test host changes ai strategy successfully`() = runTest {
        val roomRepo = FakeRoomRepository()
        val useCase = ChangeAiStrategyUseCase(roomRepo)

        val aiId = Uuid.random()
        val room = Room(
            id = roomId,
            hostId = hostId,
            gameConfig = GameConfig(config),
            playerIds = listOf(hostId, aiId),
            readyPlayerIds = listOf(aiId),
            aiPlayerStrategyKeys = mapOf(aiId to "random"),
        )
        roomRepo.setRoom(room)

        val result = useCase(roomId, hostId, aiId, "mymod:hard")
        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        assertEquals("random", result.value, "Should return the strategy key that was in effect before the change.")

        val updatedRoom = roomRepo.getRoom(roomId)
        assertNotNull(updatedRoom)
        assertEquals("mymod:hard", updatedRoom.aiPlayerStrategyKeys[aiId])
    }

    /**
     * 測試對不存在的房間執行更換策略時，應回傳 [RoomError.RoomNotFound]。
     */
    @Test
    fun `test change ai strategy fails when room does not exist`() = runTest {
        val roomRepo = FakeRoomRepository()
        val useCase = ChangeAiStrategyUseCase(roomRepo)

        val result = useCase(roomId, hostId, Uuid.random(), "random")
        assertTrue(result is Outcome.Error)
        assertEquals(RoomError.RoomNotFound(roomId), result.error)
    }

    /**
     * 測試非房主玩家無法更換 AI 策略。
     */
    @Test
    fun `test change ai strategy fails when operator is not host`() = runTest {
        val roomRepo = FakeRoomRepository()
        val useCase = ChangeAiStrategyUseCase(roomRepo)

        val guestId = Uuid.random()
        val aiId = Uuid.random()
        val room = Room(
            id = roomId,
            hostId = hostId,
            gameConfig = GameConfig(config),
            playerIds = listOf(hostId, guestId, aiId),
            aiPlayerStrategyKeys = mapOf(aiId to "random"),
        )
        roomRepo.setRoom(room)

        val result = useCase(roomId, guestId, aiId, "mymod:hard")
        assertTrue(result is Outcome.Error)
        assertEquals(RoomError.NotHost(guestId), result.error)
    }

    /**
     * 測試目標玩家不在房間內時，應回傳 [RoomError.PlayerNotInRoom]。
     */
    @Test
    fun `test change ai strategy fails when target not in room`() = runTest {
        val roomRepo = FakeRoomRepository()
        val useCase = ChangeAiStrategyUseCase(roomRepo)

        val room = Room(id = roomId, hostId = hostId, gameConfig = GameConfig(config), playerIds = listOf(hostId))
        roomRepo.setRoom(room)

        val strangerId = Uuid.random()
        val result = useCase(roomId, hostId, strangerId, "mymod:hard")
        assertTrue(result is Outcome.Error)
        assertEquals(RoomError.PlayerNotInRoom(strangerId, roomId), result.error)
    }

    /**
     * 測試目標玩家在房間內但不是 AI 時，應回傳 [RoomError.NotAiPlayer]。
     */
    @Test
    fun `test change ai strategy fails when target is not ai`() = runTest {
        val roomRepo = FakeRoomRepository()
        val useCase = ChangeAiStrategyUseCase(roomRepo)

        val guestId = Uuid.random()
        val room = Room(
            id = roomId,
            hostId = hostId,
            gameConfig = GameConfig(config),
            playerIds = listOf(hostId, guestId),
        )
        roomRepo.setRoom(room)

        val result = useCase(roomId, hostId, guestId, "mymod:hard")
        assertTrue(result is Outcome.Error)
        assertEquals(RoomError.NotAiPlayer(guestId, roomId), result.error)
    }
}
