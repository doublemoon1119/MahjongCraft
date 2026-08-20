package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomError
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
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

    private fun readyRoom(gameConfig: GameConfig = GameConfig(RiichiRuleConfig())): Room = Room(
        id = roomId,
        hostId = hostId,
        gameConfig = gameConfig,
        playerIds = listOf(hostId) + guestIds,
        readyPlayerIds = guestIds,
    )

    private class Fixtures {
        val store = AuthoritativeStateStore()
        val roomRepo = RoomRepositoryImpl(store)
        val gameRepo = GameRepositoryImpl(store)
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val useCase = StartGameUseCase(store, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher)
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

    /** 驗證開局時依流程設定為所有玩家初始化整場共用的剩餘保留思考時間。 */
    @Test
    fun `test start game initializes reserve time for all players`() = runTest {
        val fixtures = Fixtures()
        fixtures.roomRepo.setRoom(
            readyRoom(
                GameConfig(
                    RiichiRuleConfig(),
                    GameFlowConfig(
                        timeControl = ActionTimeControl.Custom(baseSeconds = 5, reserveSeconds = 37),
                    ),
                ),
            ),
        )

        assertTrue(fixtures.useCase(roomId, hostId) is Outcome.Success)

        val game = assertNotNull(fixtures.gameRepo.getGame(roomId))
        assertEquals(game.tableState.players.associate { it.id to 37_000L }, game.remainingReserveMillisByPlayerId)
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
            assertTrue(GameAction.GameStarted in fixtures.eventPublisher.getNotifiedActions(roomId, playerId, hostId))
        }
    }

    /**
     * 驗證開局後座位傳送呈現收到依 `TableState.players` 固定座位順序排列的完整玩家清單。
     */
    @Test
    fun `test start game publishes game started seating in seat order`() = runTest {
        val fixtures = Fixtures()
        fixtures.roomRepo.setRoom(readyRoom())

        fixtures.useCase(roomId, hostId)

        val tableState = assertNotNull(fixtures.gameRepo.getTableState(roomId))
        val seating = fixtures.presentationPublisher.getPublishedGameStartedSeating(roomId)
        assertEquals(tableState.players.map { it.id }, seating)
    }

    /**
     * 驗證開局呈現牌牆結構時，同時帶上開局當下就該公開翻面的王牌 Uuid 集合（日麻的第一張寶牌指示牌）
     * ——不是空集合，代表 [StartGameUseCase] 真的有透過 `TileWallRevealable` 算出目前該公開的牌。
     */
    @Test
    fun `test start game publishes the first revealed dead wall tile`() = runTest {
        val fixtures = Fixtures()
        fixtures.roomRepo.setRoom(readyRoom())

        fixtures.useCase(roomId, hostId)

        val revealedTileIds = fixtures.presentationPublisher.getPublishedWallStructureContext(roomId)?.revealedTileIds
        assertNotNull(revealedTileIds)
        assertEquals(1, revealedTileIds.size)
    }

    /**
     * 驗證開局呈現初次發牌動畫時，每個座位都帶上完整的最終手牌，且批次大小依日麻規則模組固定為
     * `[4, 4, 4, 1]`（總和等於 13 張初始手牌）——確認 [StartGameUseCase] 真的呼叫
     * `publishInitialDealAnimation`（而不是逐座位呼叫 `publishPlayerAreaUpdated`）。
     */
    @Test
    fun `test start game publishes initial deal animation with batched hands`() = runTest {
        val fixtures = Fixtures()
        fixtures.roomRepo.setRoom(readyRoom())

        fixtures.useCase(roomId, hostId)

        val tableState = assertNotNull(fixtures.gameRepo.getTableState(roomId))
        val dealAnimation = assertNotNull(fixtures.presentationPublisher.getPublishedInitialDealAnimation(roomId))
        assertEquals(listOf(4, 4, 4, 1), dealAnimation.dealBatchSizes)
        assertEquals(tableState.players.size, dealAnimation.handTileIdsBySeatIndex.size)
        tableState.players.forEachIndexed { seatIndex, player ->
            assertEquals(player.hand.tiles.map { it.id }, dealAnimation.handTileIdsBySeatIndex[seatIndex])
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
        fixtures.roomRepo.setRoom(readyRoom().copy(readyPlayerIds = emptyList()))

        val result = fixtures.useCase(roomId, hostId)

        assertTrue(result is Outcome.Error)
        assertEquals(RoomError.RoomNotReadyToStart(roomId), result.error)
    }

    /**
     * 驗證人數不在規則允許區間內時回傳 [RoomError.RoomPlayerCountInvalid]，而不是與「還有人未準備」
     * 混用同一個錯誤——即使所有目前在場的玩家都已經準備好，人數不足時仍然不能開局。
     */
    @Test
    fun `test start game fails when player count invalid`() = runTest {
        val fixtures = Fixtures()
        val understaffedRoom = readyRoom().let { room ->
            val remainingGuest = guestIds.first()
            room.copy(playerIds = listOf(hostId, remainingGuest), readyPlayerIds = listOf(remainingGuest))
        }
        fixtures.roomRepo.setRoom(understaffedRoom)

        val result = fixtures.useCase(roomId, hostId)

        assertTrue(result is Outcome.Error)
        assertEquals(RoomError.RoomPlayerCountInvalid(roomId), result.error)
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
