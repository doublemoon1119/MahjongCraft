package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
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
 * [RespondToDiscardUseCase] 的單元測試類別。
 *
 * 驗證捨牌反應視窗的回應、過水碰記錄、碰/槓優先於吃的結算順序、多人等待到齊才結算、
 * 包牌責任觸發、一發失效，以及各種驗證失敗案例。
 */
class RespondToDiscardUseCaseTest {

    private val gameId = Uuid.random()
    private val discarderId = Uuid.random()
    private val responderId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val eventPublisher = FakeGameEventPublisher()
        val useCase = RespondToDiscardUseCase(gameRepo, moduleRegistry, snapshotRepo, eventPublisher)
    }

    /**
     * 驗證唯一有資格的玩家選擇過牌時：若原本可以碰，記錄過水碰；且因反應視窗已經齊全（無人動作），
     * 直接推進到下一位玩家並清除反應視窗。
     */
    @Test
    fun `test pass on lone pon-eligible player records passed tile and advances turn`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile)
        )
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(FakeIdentifiedTileFactory.create(Tile.Honor.White), FakeIdentifiedTileFactory.create(Tile.Honor.White)))
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId))
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Pass)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(newState.pendingReaction, "Everyone eligible has responded, so the reaction window should close.")
        assertEquals(1, newState.currentPlayerIndex, "No one acted, so turn should advance like the normal discard flow.")
        val updatedResponder = newState.players.first { it.id == responderId }
        assertTrue(
            Tile.Honor.White in updatedResponder.passedTilesInRound,
            "Passing on an available Pon should be recorded as a temporary pass (過水碰)."
        )
    }

    /**
     * 驗證單一有資格的玩家選擇碰牌時，正確套用副露、標記捨牌已被鳴走，並將回合交給碰牌的玩家。
     */
    @Test
    fun `test pon success applies meld marks discard taken and advances to winner`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile)
        )
        val handTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val handTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(handTile1, handTile2))
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId))
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Pon(discardedTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(newState.pendingReaction)
        assertEquals(1, newState.currentPlayerIndex, "Turn should move to the player who claimed the meld.")

        val winner = newState.players.first { it.id == responderId }
        assertTrue(winner.hand.tiles.isEmpty(), "Both matching hand tiles should be consumed into the meld.")
        val meld = winner.hand.melds.single()
        assertEquals(MeldType.PON, meld.type)
        assertEquals(setOf(handTile1, handTile2, discardedTile), meld.tiles.toSet())

        val newDiscarder = newState.players.first { it.id == discarderId }
        assertTrue(newDiscarder.discardPile.entries.last().isTaken, "The claimed discard should be marked as taken.")
    }

    /**
     * 驗證唯一有資格的玩家選擇吃牌時，正確套用順子副露。
     */
    @Test
    fun `test chi success applies meld`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile)
        )
        val handTile4 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4))
        val handTile6 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6))
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(handTile4, handTile6))
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId))
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Chi(discardedTile.id, listOf(handTile4.id, handTile6.id)))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val winner = newState.players.first { it.id == responderId }
        assertTrue(winner.hand.tiles.isEmpty())
        val meld = winner.hand.melds.single()
        assertEquals(MeldType.CHI, meld.type)
        assertEquals(setOf(handTile4, handTile6, discardedTile), meld.tiles.toSet())
        assertEquals(1, newState.currentPlayerIndex)
    }

    /**
     * 驗證同時有玩家可以碰、也有玩家可以吃時，必須等待所有有資格的玩家都回應完畢才會結算，
     * 且結算時碰優先於吃：吃的那位玩家不會套用副露，回合交給碰牌的玩家。
     */
    @Test
    fun `test pon beats chi and resolution waits for all eligible responses`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile)
        )
        // 上家：可以吃（4萬、6萬）
        val chiTile4 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4))
        val chiTile6 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6))
        val chiPlayerId = Uuid.random()
        val chiPlayer = FakeMahjongPlayerFactory.create(
            id = chiPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(chiTile4, chiTile6))
        )
        // 另一位玩家：可以碰（兩張 5萬）
        val ponTile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val ponTile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val ponPlayerId = Uuid.random()
        val ponPlayer = FakeMahjongPlayerFactory.create(
            id = ponPlayerId,
            initialSeat = Wind.WEST,
            hand = Hand(tiles = listOf(ponTile1, ponTile2))
        )
        val bystander = FakeMahjongPlayerFactory.create(id = Uuid.random(), initialSeat = Wind.NORTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, chiPlayer, ponPlayer, bystander),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(chiPlayerId, ponPlayerId))
        )
        fixtures.gameRepo.setTableState(table)

        // 上家先回應吃：資格尚未齊全，不應結算
        val firstResult = fixtures.useCase(gameId, chiPlayerId, GameAction.Chi(discardedTile.id, listOf(chiTile4.id, chiTile6.id)))
        assertTrue(firstResult is Outcome.Success, "Expected Success but got $firstResult")
        val stateAfterChiResponse = fixtures.gameRepo.getTableState(gameId)!!
        assertNotNull(stateAfterChiResponse.pendingReaction, "Should still wait for the Pon-eligible player to respond.")
        assertEquals(0, stateAfterChiResponse.currentPlayerIndex, "Turn should not advance until resolution.")

        // 碰的玩家回應：資格齊全，開始結算，碰優先於吃
        val secondResult = fixtures.useCase(gameId, ponPlayerId, GameAction.Pon(discardedTile.id))
        assertTrue(secondResult is Outcome.Success, "Expected Success but got $secondResult")
        val finalState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(finalState.pendingReaction)

        val finalChiPlayer = finalState.players.first { it.id == chiPlayerId }
        assertTrue(finalChiPlayer.hand.melds.isEmpty(), "The Chi response should not be applied since Pon takes priority.")
        assertEquals(listOf(chiTile4, chiTile6), finalChiPlayer.hand.tiles, "The Chi player's hand should remain untouched.")

        val finalPonPlayer = finalState.players.first { it.id == ponPlayerId }
        assertEquals(MeldType.PON, finalPonPlayer.hand.melds.single().type)
        assertEquals(
            finalState.players.indexOfFirst { it.id == ponPlayerId },
            finalState.currentPlayerIndex,
            "Turn should go to the player who claimed the meld."
        )
    }

    /**
     * 驗證碰第三組三元牌、湊齊大三元時，會觸發包牌責任並寫入碰牌玩家的規則狀態。
     */
    @Test
    fun `test pon triggers pao liability`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile)
        )
        val handTiles = listOf(
            Tile.Honor.Red, Tile.Honor.Red, Tile.Honor.Red,
            Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
            Tile.Honor.White, Tile.Honor.White
        ).map { FakeIdentifiedTileFactory.create(it) }
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = handTiles),
            playerRuleState = RiichiPlayerState()
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId))
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Pon(discardedTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val winner = newState.players.first { it.id == responderId }
        val riichiState = winner.playerRuleState as RiichiPlayerState
        assertEquals(
            PaoLiability(PaoYaku.Daisangen, newState.relativeDirectionOf(responderId, discarderId)),
            riichiState.paoLiability
        )
    }

    /**
     * 驗證任何一次鳴牌都會讓場上所有仍在一發窗口內的玩家（不只鳴牌的當事人）一發失效。
     */
    @Test
    fun `test meld claim voids ippatsu for all affected players`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
            playerRuleState = RiichiPlayerState(
                riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East),
                isIppatsu = true
            )
        )
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(FakeIdentifiedTileFactory.create(Tile.Honor.White), FakeIdentifiedTileFactory.create(Tile.Honor.White)))
        )
        val bystanderId = Uuid.random()
        val bystander = FakeMahjongPlayerFactory.create(
            id = bystanderId,
            initialSeat = Wind.WEST,
            playerRuleState = RiichiPlayerState(
                riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.South),
                isIppatsu = true
            )
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder, bystander),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId))
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Pon(discardedTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val newDiscarder = newState.players.first { it.id == discarderId }
        val newBystander = newState.players.first { it.id == bystanderId }
        assertEquals(false, (newDiscarder.playerRuleState as RiichiPlayerState).isIppatsu)
        assertEquals(false, (newBystander.playerRuleState as RiichiPlayerState).isIppatsu)
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test respond fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId, responderId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }

    /**
     * 驗證發起請求的玩家不在該對局中時回傳 [GameError.PlayerNotInGame]。
     */
    @Test
    fun `test respond fails when player not in game`() = runTest {
        val fixtures = Fixtures()
        val discarder = FakeMahjongPlayerFactory.create(id = discarderId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(discarder))
        fixtures.gameRepo.setTableState(table)
        val strangerId = Uuid.random()

        val result = fixtures.useCase(gameId, strangerId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.PlayerNotInGame(strangerId, gameId), result.error)
    }

    /**
     * 驗證目前沒有反應視窗時回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test respond fails when there is no pending reaction`() = runTest {
        val fixtures = Fixtures()
        val discarder = FakeMahjongPlayerFactory.create(id = discarderId, initialSeat = Wind.EAST)
        val responder = FakeMahjongPlayerFactory.create(id = responderId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(discarder, responder), pendingReaction = null)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(responderId, gameId, GameAction.Pass), result.error)
    }

    /**
     * 驗證玩家不在本次反應視窗的資格名單中時回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test respond fails when player is not eligible`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile)
        )
        val ineligiblePlayerId = Uuid.random()
        val ineligiblePlayer = FakeMahjongPlayerFactory.create(id = ineligiblePlayerId, initialSeat = Wind.SOUTH)
        val eligiblePlayer = FakeMahjongPlayerFactory.create(id = responderId, initialSeat = Wind.WEST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, ineligiblePlayer, eligiblePlayer),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId))
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, ineligiblePlayerId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(ineligiblePlayerId, gameId, GameAction.Pass), result.error)
    }

    /**
     * 驗證玩家已經回應過一次後，不能再次回應。
     */
    @Test
    fun `test respond fails when player has already responded`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile)
        )
        val otherEligibleId = Uuid.random()
        val responder = FakeMahjongPlayerFactory.create(id = responderId, initialSeat = Wind.SOUTH)
        val otherEligible = FakeMahjongPlayerFactory.create(id = otherEligibleId, initialSeat = Wind.WEST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder, otherEligible),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(
                discarderId,
                discardedTile.id,
                setOf(responderId, otherEligibleId),
                responses = mapOf(responderId to GameAction.Pass)
            )
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(responderId, gameId, GameAction.Pass), result.error)
    }

    /**
     * 驗證回應的動作不在目前合法動作清單中時（例如玩家手牌根本無法組成該副露）回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test respond fails when action is not actually legal`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile)
        )
        // 手牌沒有任何白板，無法碰
        val responder = FakeMahjongPlayerFactory.create(id = responderId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId))
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Pon(discardedTile.id))

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(responderId, gameId, GameAction.Pon(discardedTile.id)), result.error)
    }
}
