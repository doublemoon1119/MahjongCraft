package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [DeclareRiichiUseCase] 的單元測試類別。
 *
 * 驗證立直宣告的業務邏輯，包含合法性驗證（聽牌、門前清、點數、特定捨牌是否仍聽牌）、
 * 立直/雙立直的判斷、點數與立直棒的異動，以及快照與事件的同步行為。
 */
class DeclareRiichiUseCaseTest {

    private val gameId = Uuid.random()
    private val currentPlayerId = Uuid.random()
    private val otherPlayerId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val eventPublisher = FakeGameEventPublisher()
        val useCase = DeclareRiichiUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher)
    }

    // 111萬 234567899萬（13 張，單騎聽 8 萬）+ 摸到北風
    private val manTiles = listOf(1, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 9)
        .map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, it)) }
    private val drawnTile = FakeIdentifiedTileFactory.create(Tile.Honor.North)

    private fun tenpaiHand(): Hand = Hand(tiles = manTiles, lastDrawn = drawnTile)

    private fun createRiichiPlayer(
        id: Uuid = currentPlayerId,
        hand: Hand = tenpaiHand(),
        score: Int = 25000,
        playerRuleState: RiichiPlayerState = RiichiPlayerState(),
        discardPile: RiichiDiscardPile = RiichiDiscardPile(),
    ): MahjongPlayer = FakeMahjongPlayerFactory.create(
        id = id,
        initialSeat = Wind.EAST,
        hand = hand,
        discardPile = discardPile,
        playerRuleState = playerRuleState,
    ).copy(score = score)

    /**
     * 驗證聽牌、門前清、點數足夠的玩家，宣告立直（打出剛摸到的牌）後，
     * 手牌、牌河、點數、立直棒、玩家立直狀態、回合推進皆正確更新。
     */
    @Test
    fun `test declare riichi with tsumogiri updates all riichi related state`() = runTest {
        val fixtures = Fixtures()
        // 已經打過一輪牌，確保不是雙立直
        val alreadyDiscardedPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.West))
        val currentPlayer = createRiichiPlayer(discardPile = alreadyDiscardedPile)
        val otherPlayer = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
            .let { it.copy(discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))) }
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        val updatedPlayer = newState.players.first { it.id == currentPlayerId }

        assertEquals(null, updatedPlayer.hand.lastDrawn)
        assertEquals(manTiles, updatedPlayer.hand.tiles)
        // 已經有一張牌（打過一輪）+ 這次立直宣告打出的牌
        assertEquals(2, updatedPlayer.discardPile.entries.size)
        val riichiEntry = updatedPlayer.discardPile.entries.last() as RiichiDiscardEntry
        assertEquals(drawnTile, riichiEntry.tile)
        assertTrue(riichiEntry.isRiichi)
        assertEquals(25000 - 1000, updatedPlayer.score)

        val riichiState = updatedPlayer.playerRuleState as RiichiPlayerState
        assertEquals(drawnTile, riichiState.riichiTile)
        assertNull(
            riichiState.doubleRiichiTile,
            "This is not the first go-around, so it should not be a double riichi.",
        )
        assertTrue(riichiState.isIppatsu)

        val dynamicState = newState.dynamicRuleState as RiichiDynamicState
        assertEquals(1, dynamicState.riichiStickCount)

        assertEquals(1, newState.currentPlayerIndex, "Turn should advance to the next player.")
        assertEquals(GameAction.Riichi, updatedPlayer.actionHistory[updatedPlayer.actionHistory.size - 2])
        assertEquals(GameAction.Discard(drawnTile.id), updatedPlayer.actionHistory.last())
    }

    /**
     * 驗證在場上尚無任何鳴牌、且自己是第一次打牌時宣告立直，會被判定為雙立直。
     */
    @Test
    fun `test declare riichi on first go-around is a double riichi`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = createRiichiPlayer()
        val otherPlayer = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val updatedPlayer = fixtures.gameRepo.getTableState(gameId)!!.players.first { it.id == currentPlayerId }
        val riichiState = updatedPlayer.playerRuleState as RiichiPlayerState
        assertEquals(drawnTile, riichiState.doubleRiichiTile)
        assertNull(riichiState.riichiTile)
    }

    /**
     * 驗證捨牌後所有觀察者的快照皆同步更新，且所有玩家皆先收到 Riichi、再收到 Discard 事件通知。
     */
    @Test
    fun `test declare riichi syncs snapshot and notifies riichi then discard`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = createRiichiPlayer()
        val otherPlayer = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)
        fixtures.snapshotRepo.setSnapshot(currentPlayerId, table.toSnapshot(currentPlayerId))
        fixtures.snapshotRepo.setSnapshot(otherPlayerId, table.toSnapshot(otherPlayerId))

        fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, currentPlayerId))
        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, otherPlayerId))
        assertEquals(
            listOf(GameAction.Riichi, GameAction.Discard(drawnTile.id)),
            fixtures.eventPublisher.getNotifiedActions(gameId, otherPlayerId, currentPlayerId),
        )
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test declare riichi fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }

    /**
     * 驗證發起請求的玩家不在該對局中時回傳 [GameError.PlayerNotInGame]。
     */
    @Test
    fun `test declare riichi fails when player not in game`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = createRiichiPlayer()
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        )
        fixtures.gameRepo.setTableState(table)
        val strangerId = Uuid.random()

        val result = fixtures.useCase(gameId, strangerId, drawnTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.PlayerNotInGame(strangerId, gameId), result.error)
    }

    /**
     * 驗證非當前回合玩家嘗試宣告立直時回傳 [GameError.NotPlayersTurn]。
     */
    @Test
    fun `test declare riichi fails when not players turn`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = createRiichiPlayer()
        val otherPlayer = createRiichiPlayer(id = otherPlayerId, hand = Hand(tiles = manTiles, lastDrawn = drawnTile))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, otherPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.NotPlayersTurn(otherPlayerId, gameId), result.error)
    }

    /**
     * 驗證玩家尚未摸牌就嘗試宣告立直時回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test declare riichi fails when player has not drawn yet`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = createRiichiPlayer(hand = Hand(tiles = manTiles))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(currentPlayerId, gameId, GameAction.Riichi),
            result.error,
        )
    }

    /**
     * 驗證玩家已經立直過時，不能再次宣告立直。
     */
    @Test
    fun `test declare riichi fails when already riichi`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = createRiichiPlayer(
            playerRuleState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(currentPlayerId, gameId, GameAction.Riichi),
            result.error,
        )
    }

    /**
     * 驗證手牌非門前清（有非暗槓的副露）時不能宣告立直。
     */
    @Test
    fun `test declare riichi fails when hand is not menzen`() = runTest {
        val fixtures = Fixtures()
        val meldTiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }
        val notMenzenHand = tenpaiHand().call(
            type = MeldType.PON,
            tiles = meldTiles,
            source = meldTiles[0],
            direction = RelativeDirection.Left,
        )
        val currentPlayer = createRiichiPlayer(hand = notMenzenHand)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(currentPlayerId, gameId, GameAction.Riichi),
            result.error,
        )
    }

    /**
     * 驗證點數不足 1000 時不能宣告立直。
     */
    @Test
    fun `test declare riichi fails when score is insufficient`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = createRiichiPlayer(score = 500)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(currentPlayerId, gameId, GameAction.Riichi),
            result.error,
        )
    }

    /**
     * 驗證選擇的捨牌雖然整體聽牌可以透過其他牌達成，但打出這張特定的牌會破壞聽牌時，
     * 應回傳 [GameError.IllegalAction] 而非直接允許。
     */
    @Test
    fun `test declare riichi fails when the specific discard breaks tenpai`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = createRiichiPlayer()
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        )
        fixtures.gameRepo.setTableState(table)

        // 打出組成 111萬 刻子的其中一張 1萬，會破壞聽牌
        val result = fixtures.useCase(gameId, currentPlayerId, manTiles[0].id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(currentPlayerId, gameId, GameAction.Riichi),
            result.error,
        )
    }

    /**
     * 驗證不支援立直的規則不會讓立直宣告成立。
     */
    @Test
    fun `test declare riichi fails for a ruleset without riichi`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = manTiles, lastDrawn = drawnTile),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            config = TaiwanRuleConfig(),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(currentPlayerId, gameId, GameAction.Riichi),
            result.error,
        )
    }
}
