package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [DiscardTileUseCase] 的單元測試類別。
 *
 * 驗證捨牌的業務邏輯，包含回合驗證、手牌與牌河狀態更新、推進下一位玩家，
 * 以及快照與事件的同步行為。
 */
class DiscardTileUseCaseTest {

    private val gameId = Uuid.random()
    private val currentPlayerId = Uuid.random()
    private val otherPlayerId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val snapshotRepo = FakeGameSnapshotRepository()
        val eventPublisher = FakeGameEventPublisher()
        val useCase = DiscardTileUseCase(gameRepo, snapshotRepo, eventPublisher)
    }

    private val drawnTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
    private val handTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))

    /**
     * 驗證捨棄剛摸到的牌（摸切）時，正確更新手牌、牌河並推進到下一位玩家。
     */
    @Test
    fun `test discard drawn tile advances to next player`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(handTile), lastDrawn = drawnTile)
        )
        val otherPlayer = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        val updatedPlayer = newState.players.first { it.id == currentPlayerId }
        assertEquals(null, updatedPlayer.hand.lastDrawn)
        assertEquals(listOf(handTile), updatedPlayer.hand.tiles)
        assertEquals(1, updatedPlayer.discardPile.entries.size)
        assertEquals(drawnTile, updatedPlayer.discardPile.entries.first().tile)
        assertTrue(updatedPlayer.actionHistory.last() is GameAction.Discard)
        assertEquals(1, newState.currentPlayerIndex, "Turn should advance to the next player.")
    }

    /**
     * 驗證捨棄手牌中的牌（非摸切）時，剛摸到的牌會併入立牌。
     */
    @Test
    fun `test discard tile from standing hand keeps drawn tile in hand`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(handTile), lastDrawn = drawnTile)
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, handTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val updatedPlayer = fixtures.gameRepo.getTableState(gameId)!!.players.first { it.id == currentPlayerId }
        assertEquals(null, updatedPlayer.hand.lastDrawn)
        assertEquals(listOf(drawnTile), updatedPlayer.hand.tiles)
        assertEquals(handTile, updatedPlayer.discardPile.entries.first().tile)
    }

    /**
     * 驗證捨牌後所有觀察者的快照皆同步更新，且所有玩家皆收到 Discard 事件通知。
     */
    @Test
    fun `test discard tile syncs snapshot and notifies all players`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = drawnTile)
        )
        val otherPlayer = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0
        )
        fixtures.gameRepo.setTableState(table)
        fixtures.snapshotRepo.setSnapshot(currentPlayerId, table.toSnapshot(currentPlayerId))
        fixtures.snapshotRepo.setSnapshot(otherPlayerId, table.toSnapshot(otherPlayerId))

        fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, currentPlayerId))
        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, otherPlayerId))
        assertEquals(
            GameAction.Discard(drawnTile.id),
            fixtures.eventPublisher.getNotifiedAction(gameId, currentPlayerId, currentPlayerId)
        )
        assertEquals(
            GameAction.Discard(drawnTile.id),
            fixtures.eventPublisher.getNotifiedAction(gameId, otherPlayerId, currentPlayerId)
        )
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test discard tile fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }

    /**
     * 驗證發起請求的玩家不在該對局中時回傳 [GameError.PlayerNotInGame]。
     */
    @Test
    fun `test discard tile fails when player not in game`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = drawnTile)
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(currentPlayer))
        fixtures.gameRepo.setTableState(table)
        val strangerId = Uuid.random()

        val result = fixtures.useCase(gameId, strangerId, drawnTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.PlayerNotInGame(strangerId, gameId), result.error)
    }

    /**
     * 驗證非當前回合玩家嘗試捨牌時回傳 [GameError.NotPlayersTurn]。
     */
    @Test
    fun `test discard tile fails when not players turn`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = drawnTile)
        )
        val otherPlayer = FakeMahjongPlayerFactory.create(
            id = otherPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(lastDrawn = handTile)
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            currentPlayerIndex = 0
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, otherPlayerId, handTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.NotPlayersTurn(otherPlayerId, gameId), result.error)
    }

    /**
     * 驗證玩家尚未摸牌就嘗試捨牌時回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test discard tile fails when player has not drawn yet`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(handTile))
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(currentPlayer), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, handTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(currentPlayerId, gameId, GameAction.Discard(handTile.id)),
            result.error
        )
    }

    /**
     * 驗證欲捨棄的牌不存在於玩家手牌中時回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test discard tile fails when tile not found in hand`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = drawnTile)
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(currentPlayer), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)
        val unknownTileId = Uuid.random()

        val result = fixtures.useCase(gameId, currentPlayerId, unknownTileId)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(currentPlayerId, gameId, GameAction.Discard(unknownTileId)),
            result.error
        )
    }
}
