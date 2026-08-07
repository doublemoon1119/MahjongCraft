package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
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
 * [DeclareKyuushuKyuuhaiUseCase] 的單元測試類別。
 *
 * 驗證九種九牌宣告的合法性驗證（第一巡、么九牌種類數）、途中流局的結算行為
 * （莊家固定連莊、不結算任何點數、`ExhaustiveDraw` 記錄進全員的 `actionHistory`），
 * 以及快照與事件的同步行為。
 */
class DeclareKyuushuKyuuhaiUseCaseTest {

    private val gameId = Uuid.random()
    private val playerId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val eventPublisher = FakeGameEventPublisher()
        val useCase = DeclareKyuushuKyuuhaiUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher)
    }

    // 手牌（13 張，含 8 種么九牌 + 5 張非么九牌），供九種九牌測試共用
    private fun standingTiles() = listOf(
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 9),
        Tile.Numeric(Tile.Suit.Dot, 1),
        Tile.Numeric(Tile.Suit.Dot, 9),
        Tile.Numeric(Tile.Suit.Bamboo, 1),
        Tile.Numeric(Tile.Suit.Bamboo, 9),
        Tile.Honor.East,
        Tile.Honor.South,
        Tile.Numeric(Tile.Suit.Character, 4),
        Tile.Numeric(Tile.Suit.Character, 5),
        Tile.Numeric(Tile.Suit.Dot, 4),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Bamboo, 4),
    )

    // 摸到第 9 種么九牌，合計達成九種九牌門檻
    private val ninthYaochuuTile = FakeIdentifiedTileFactory.create(Tile.Honor.West)

    // 摸到非么九牌，合計只有 8 種么九牌，未達門檻
    private val nonYaochuuTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))

    private fun handWithDrawnTile(drawnTile: IdentifiedTile): Hand = FakeHandFactory.create(standingTiles()).copy(lastDrawn = drawnTile)

    /**
     * 驗證成立九種九牌時：全員 `actionHistory` 皆記錄 `ExhaustiveDraw(KyuushuKyuuhai)`、
     * 分數皆不變（途中流局不結算任何點數）。
     */
    @Test
    fun `test declare kyuushu kyuuhai records ExhaustiveDraw for all players and does not change scores`() = runTest {
        val fixtures = Fixtures()
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = handWithDrawnTile(ninthYaochuuTile),
            discardPile = RiichiDiscardPile(),
        ).copy(score = 25000)
        val others = listOf(Wind.SOUTH, Wind.WEST, Wind.NORTH).map {
            FakeMahjongPlayerFactory.create(initialSeat = it, discardPile = RiichiDiscardPile()).copy(score = 25000)
        }
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer) + others,
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val expectedAction = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai)
        newState.players.forEach { player ->
            assertEquals(expectedAction, player.actionHistory.last(), "Every player should have ExhaustiveDraw recorded, not just the declarer.")
            assertEquals(25000, player.score, "Kyuushu kyuuhai is an abortive draw and should not exchange any points.")
        }
    }

    /**
     * 驗證么九牌種類不足 9 種時，宣告不合法。
     */
    @Test
    fun `test declare kyuushu kyuuhai fails when fewer than nine yaochuu types`() = runTest {
        val fixtures = Fixtures()
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = handWithDrawnTile(nonYaochuuTile),
            discardPile = RiichiDiscardPile(),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(declarer), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.UnsupportedAction(gameId, playerId), result.error)
    }

    /**
     * 驗證不是第一巡（玩家自己已經打過一輪牌）時，宣告不合法。
     */
    @Test
    fun `test declare kyuushu kyuuhai fails when not the first go-around`() = runTest {
        val fixtures = Fixtures()
        val alreadyDiscarded = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.North))
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = handWithDrawnTile(ninthYaochuuTile),
            discardPile = alreadyDiscarded,
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(declarer), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.UnsupportedAction(gameId, playerId), result.error)
    }

    /**
     * 驗證玩家尚未摸牌就嘗試宣告時，回傳 [GameError.UnsupportedAction]。
     */
    @Test
    fun `test declare kyuushu kyuuhai fails when player has not drawn yet`() = runTest {
        val fixtures = Fixtures()
        val declarer = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, discardPile = RiichiDiscardPile())
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(declarer), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.UnsupportedAction(gameId, playerId), result.error)
    }

    /**
     * 驗證非當前回合玩家嘗試宣告時回傳 [GameError.NotPlayersTurn]。
     */
    @Test
    fun `test declare kyuushu kyuuhai fails when not players turn`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, discardPile = RiichiDiscardPile())
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.SOUTH,
            hand = handWithDrawnTile(ninthYaochuuTile),
            discardPile = RiichiDiscardPile(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, declarer),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.NotPlayersTurn(playerId, gameId), result.error)
    }

    /**
     * 驗證發起請求的玩家不在該對局中時回傳 [GameError.PlayerNotInGame]。
     */
    @Test
    fun `test declare kyuushu kyuuhai fails when player not in game`() = runTest {
        val fixtures = Fixtures()
        val declarer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, discardPile = RiichiDiscardPile())
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(declarer), config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.PlayerNotInGame(playerId, gameId), result.error)
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test declare kyuushu kyuuhai fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }

    /**
     * 驗證宣告成功後所有觀察者的快照皆同步更新，且所有玩家皆收到事件通知（actor 為宣告者本人）。
     */
    @Test
    fun `test declare kyuushu kyuuhai syncs snapshot and notifies all players`() = runTest {
        val fixtures = Fixtures()
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = handWithDrawnTile(ninthYaochuuTile),
            discardPile = RiichiDiscardPile(),
        )
        val otherId = Uuid.random()
        val other = FakeMahjongPlayerFactory.create(id = otherId, initialSeat = Wind.SOUTH, discardPile = RiichiDiscardPile())
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(declarer, other), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)
        fixtures.snapshotRepo.setSnapshot(playerId, table.toSnapshot(playerId))
        fixtures.snapshotRepo.setSnapshot(otherId, table.toSnapshot(otherId))

        fixtures.useCase(gameId, playerId)

        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, playerId))
        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, otherId))
        val expectedAction = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai)
        assertEquals(expectedAction, fixtures.eventPublisher.getNotifiedAction(gameId, playerId, playerId))
        assertEquals(expectedAction, fixtures.eventPublisher.getNotifiedAction(gameId, otherId, playerId))
    }
}
