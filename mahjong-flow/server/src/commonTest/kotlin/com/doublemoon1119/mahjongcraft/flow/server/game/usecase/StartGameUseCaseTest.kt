package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [StartGameUseCase] 的單元測試類別。
 *
 * 驗證開局的業務邏輯，包含房間驗證、桌況初始化、Room→Game 的識別碼搬家、
 * 快照同步與事件廣播行為。
 */
class StartGameUseCaseTest {

    private val hostId = Uuid.random()
    private val guestIds = List(3) { Uuid.random() }
    private val roomId = Uuid.random()

    private fun readyRoom(): Room = Room(
        id = roomId,
        hostId = hostId,
        gameConfig = GameConfig(RiichiRuleConfig()),
        playerIds = (listOf(hostId) + guestIds).toSet(),
        readyPlayerIds = guestIds.toSet(),
    )

    private class Fixtures {
        val store = AuthoritativeStateStore()
        val roomRepo = RoomRepositoryImpl(store)
        val gameRepo = GameRepositoryImpl(store)
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val eventPublisher = FakeGameEventPublisher()
        val useCase = StartGameUseCase(store, moduleRegistry, snapshotRepo, eventPublisher)
    }

    /**
     * 驗證房間準備完成時，開局成功並將識別碼從 Room 搬家到 Game。
     */
    @Test
    fun `test start game moves room id to game repository`() = runTest {
        val fixtures = Fixtures()
        fixtures.roomRepo.setRoom(readyRoom())

        val result = fixtures.useCase(roomId, hostId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        assertEquals(roomId, result.value)

        assertNull(fixtures.roomRepo.getRoom(roomId), "Room should be removed once the game has started.")
        val tableState = fixtures.gameRepo.getTableState(roomId)
        assertNotNull(tableState, "A TableState should be created at the same id as the room.")
        assertEquals(roomId, tableState.id)
        assertEquals(setOf(hostId) + guestIds.toSet(), tableState.players.map { it.id }.toSet())
    }

    /**
     * 驗證開局後每位玩家皆收到一份對局快照，且快照內容對應正確的對局 id。
     */
    @Test
    fun `test start game syncs snapshot to every player`() = runTest {
        val fixtures = Fixtures()
        fixtures.roomRepo.setRoom(readyRoom())

        fixtures.useCase(roomId, hostId)

        (listOf(hostId) + guestIds).forEach { playerId ->
            val snapshot = fixtures.snapshotRepo.getSnapshot(roomId, playerId)
            assertNotNull(snapshot, "Player $playerId should receive a game snapshot.")
            assertEquals(roomId, snapshot.id)
        }
    }

    /**
     * 驗證開局後每位玩家皆收到 GameStarted 事件通知。
     */
    @Test
    fun `test start game notifies every player with GameStarted`() = runTest {
        val fixtures = Fixtures()
        fixtures.roomRepo.setRoom(readyRoom())

        fixtures.useCase(roomId, hostId)

        (listOf(hostId) + guestIds).forEach { playerId ->
            assertEquals(
                GameAction.GameStarted,
                fixtures.eventPublisher.getNotifiedAction(roomId, playerId, hostId),
            )
        }
    }

    /**
     * 驗證房間不存在時回傳 [RoomError.RoomNotFound]。
     */
    @Test
    fun `test start game fails when room not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(roomId, hostId)

        assertTrue(result is Outcome.Error)
        assertEquals(RoomError.RoomNotFound(roomId), result.error)
    }

    /**
     * 驗證非房主發起開局時回傳 [RoomError.NotHost]。
     */
    @Test
    fun `test start game fails when operator is not host`() = runTest {
        val fixtures = Fixtures()
        fixtures.roomRepo.setRoom(readyRoom())
        val impostor = Uuid.random()

        val result = fixtures.useCase(roomId, impostor)

        assertTrue(result is Outcome.Error)
        assertEquals(RoomError.NotHost(impostor), result.error)
        assertNotNull(fixtures.roomRepo.getRoom(roomId), "Room should not be consumed on a failed start attempt.")
    }

    /**
     * 驗證房間尚未準備好（仍有玩家未準備）時回傳 [RoomError.RoomNotReadyToStart]。
     */
    @Test
    fun `test start game fails when room not ready to start`() = runTest {
        val fixtures = Fixtures()
        fixtures.roomRepo.setRoom(readyRoom().copy(readyPlayerIds = emptySet()))

        val result = fixtures.useCase(roomId, hostId)

        assertTrue(result is Outcome.Error)
        assertEquals(RoomError.RoomNotReadyToStart(roomId), result.error)
    }

    /**
     * 驗證開局成功後，同一房間 id 再次嘗試開局會因為 Room 已被移除而回傳 RoomNotFound，
     * 而非重複初始化出另一場對局。
     */
    @Test
    fun `test starting game twice does not reinitialize the game`() = runTest {
        val fixtures = Fixtures()
        fixtures.roomRepo.setRoom(readyRoom())

        val firstResult = fixtures.useCase(roomId, hostId)
        assertTrue(firstResult is Outcome.Success)
        val firstTableState = fixtures.gameRepo.getTableState(roomId)

        val secondResult = fixtures.useCase(roomId, hostId)

        assertTrue(secondResult is Outcome.Error)
        assertEquals(RoomError.RoomNotFound(roomId), secondResult.error)
        assertEquals(firstTableState, fixtures.gameRepo.getTableState(roomId), "The existing game should be untouched.")
    }
}
