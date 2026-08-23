package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
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
 * [DeclareTsumoUseCase] 的單元測試類別。
 *
 * 驗證自摸胡牌結算的業務邏輯，包含依莊/閒身分或包牌責任分攤點數、贏家的 actionHistory 記錄、
 * 「這次範圍只改分數」的迴歸驗證、快照與事件的同步行為，以及各種驗證失敗案例。
 */
class DeclareTsumoUseCaseTest {

    private val gameId = Uuid.random()
    private val winnerId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val useCase = DeclareTsumoUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher)
    }

    // 中中、發發發、白白白、123m、55p（大三元役滿，13 張立牌）
    private val daisangenTiles = listOf(
        Tile.Honor.Red, Tile.Honor.Red,
        Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
        Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 5),
    )
    private val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)

    private fun daisangenHand(): Hand = Hand(
        tiles = daisangenTiles.map { FakeIdentifiedTileFactory.create(it) },
        lastDrawn = winningTile,
    )

    // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和疊加成雙倍役滿
    private fun priorDiscardPile(): RiichiDiscardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South))

    /**
     * 驗證莊家自摸時，其餘三位閒家平均分攤點數，且贏家取得對應點數。
     */
    @Test
    fun `test declare tsumo splits payment evenly among three non-dealers when winner is dealer`() = runTest {
        val fixtures = Fixtures()
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.EAST,
            hand = daisangenHand(),
            discardPile = priorDiscardPile(),
            playerRuleState = RiichiPlayerState(),
        ).copy(score = 25000)
        val south = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH).copy(score = 25000)
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST).copy(score = 25000)
        val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH).copy(score = 25000)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(winner, south, west, north),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, winnerId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(25000 + 48000, newState.players.first { it.id == winnerId }.score)
        assertEquals(25000 - 16000, newState.players.first { it.id == south.id }.score)
        assertEquals(25000 - 16000, newState.players.first { it.id == west.id }.score)
        assertEquals(25000 - 16000, newState.players.first { it.id == north.id }.score)
    }

    /**
     * 驗證閒家自摸時，莊家與另外兩位閒家支付不同金額。
     */
    @Test
    fun `test declare tsumo charges dealer and non-dealers differently for non-dealer tsumo`() = runTest {
        val fixtures = Fixtures()
        val dealer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST).copy(score = 25000)
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.SOUTH,
            hand = daisangenHand(),
            discardPile = priorDiscardPile(),
            playerRuleState = RiichiPlayerState(),
        ).copy(score = 25000)
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST).copy(score = 25000)
        val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH).copy(score = 25000)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealer, winner, west, north),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 1,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, winnerId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(25000 + 32000, newState.players.first { it.id == winnerId }.score)
        assertEquals(25000 - 16000, newState.players.first { it.id == dealer.id }.score)
        assertEquals(25000 - 8000, newState.players.first { it.id == west.id }.score)
        assertEquals(25000 - 8000, newState.players.first { it.id == north.id }.score)
    }

    /**
     * 驗證包牌責任已成立時，只有包牌責任者的分數變動，其餘玩家分數維持不變。
     */
    @Test
    fun `test declare tsumo only debits the pao-liable player`() = runTest {
        val fixtures = Fixtures()
        val paoPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST).copy(score = 25000)
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.SOUTH,
            hand = daisangenHand(),
            discardPile = priorDiscardPile(),
            playerRuleState = RiichiPlayerState(paoLiability = PaoLiability(PaoYaku.Daisangen, RelativeDirection.Left)),
        ).copy(score = 25000)
        val other1 = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST).copy(score = 25000)
        val other2 = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH).copy(score = 25000)
        // paoPlayer 排在 winner 前一位（座位順序），故對 winner 而言方位為 Left，與上方宣告的包牌責任相符
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(paoPlayer, winner, other1, other2),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 1,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, winnerId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(25000 + 32000, newState.players.first { it.id == winnerId }.score)
        assertEquals(25000 - 32000, newState.players.first { it.id == paoPlayer.id }.score)
        assertEquals(25000, newState.players.first { it.id == other1.id }.score, "Non-pao players should not pay anything.")
        assertEquals(25000, newState.players.first { it.id == other2.id }.score, "Non-pao players should not pay anything.")
    }

    /**
     * 驗證自摸成功後，贏家的 actionHistory 會記錄 [GameAction.Tsumo]。
     */
    @Test
    fun `test declare tsumo records Tsumo in winner's action history`() = runTest {
        val fixtures = Fixtures()
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.EAST,
            hand = daisangenHand(),
            discardPile = priorDiscardPile(),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(winner),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.useCase(gameId, winnerId)

        val updatedWinner = fixtures.gameRepo.getTableState(gameId)!!.players.first { it.id == winnerId }
        assertEquals(GameAction.Tsumo, updatedWinner.actionHistory.last())
    }

    /**
     * 驗證自摸贏家收下場上所有立直棒（分數含供託金額、riichiStickCount 歸零），但不動
     * currentPlayerIndex、pendingReaction，這些留給後續（連莊/過莊/開新局）的 use case 處理。
     */
    @Test
    fun `test declare tsumo collects stick pot but does not change currentPlayerIndex or pendingReaction`() = runTest {
        val fixtures = Fixtures()
        val dealer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.SOUTH,
            hand = daisangenHand(),
            discardPile = priorDiscardPile(),
            playerRuleState = RiichiPlayerState(),
        )
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealer, winner, west, north),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 2),
            currentPlayerIndex = 1,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, winnerId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(1, newState.currentPlayerIndex, "currentPlayerIndex should be left untouched by this unit's scope.")
        assertNull(newState.pendingReaction)
        assertEquals(0, (newState.dynamicRuleState as RiichiDynamicState).riichiStickCount, "The winner should collect all sticks on the table.")
        assertEquals(
            32000 + 2000,
            newState.players.first { it.id == winnerId }.score,
            "Winner's score should include both the tsumo total and the 2 sticks (1000 each).",
        )
    }

    /**
     * 驗證自摸成功後所有觀察者的快照皆同步更新，且所有玩家皆收到 Tsumo 事件通知。
     */
    @Test
    fun `test declare tsumo syncs snapshot and notifies all players`() = runTest {
        val fixtures = Fixtures()
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.EAST,
            hand = daisangenHand(),
            discardPile = priorDiscardPile(),
            playerRuleState = RiichiPlayerState(),
        )
        val otherPlayerId = Uuid.random()
        val other = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(winner, other),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)
        fixtures.snapshotRepo.setSnapshot(winnerId, table.toSnapshot(setOf(winnerId)))
        fixtures.snapshotRepo.setSnapshot(otherPlayerId, table.toSnapshot(setOf(otherPlayerId)))

        fixtures.useCase(gameId, winnerId)

        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, winnerId))
        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, otherPlayerId))
        assertEquals(GameAction.Tsumo, fixtures.eventPublisher.getNotifiedAction(gameId, winnerId, winnerId))
        assertEquals(GameAction.Tsumo, fixtures.eventPublisher.getNotifiedAction(gameId, otherPlayerId, winnerId))
    }

    /**
     * 驗證自摸成功後觸發胡牌慶祝演出，帶上正確的贏家座位、胡牌張 Uuid，且 `isTsumo` 為 `true`。
     */
    @Test
    fun `test declare tsumo publishes win celebration with winner seat and winning tile`() = runTest {
        val fixtures = Fixtures()
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.SOUTH,
            hand = daisangenHand(),
            discardPile = priorDiscardPile(),
            playerRuleState = RiichiPlayerState(),
        )
        val dealer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealer, winner),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 1,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, winnerId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val celebrations = fixtures.presentationPublisher.getPublishedWinCelebrations(gameId)
        assertEquals(1, celebrations.size)
        assertEquals(1, celebrations.single().winnerSeatIndex)
        assertEquals(winningTile.id, celebrations.single().winningTileId)
        assertTrue(celebrations.single().isTsumo)
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test declare tsumo fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId, winnerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }

    /**
     * 驗證發起請求的玩家不在該對局中時回傳 [GameError.PlayerNotInGame]。
     */
    @Test
    fun `test declare tsumo fails when player not in game`() = runTest {
        val fixtures = Fixtures()
        val winner = FakeMahjongPlayerFactory.create(id = winnerId, initialSeat = Wind.EAST, hand = daisangenHand())
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(winner), config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)
        val strangerId = Uuid.random()

        val result = fixtures.useCase(gameId, strangerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.PlayerNotInGame(strangerId, gameId), result.error)
    }

    /**
     * 驗證非當前回合玩家嘗試宣告自摸時回傳 [GameError.NotPlayersTurn]。
     */
    @Test
    fun `test declare tsumo fails when not players turn`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))),
        )
        val winner = FakeMahjongPlayerFactory.create(id = winnerId, initialSeat = Wind.SOUTH, hand = daisangenHand())
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, winner),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, winnerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.NotPlayersTurn(winnerId, gameId), result.error)
    }

    /**
     * 驗證玩家尚未摸牌就嘗試宣告自摸時回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test declare tsumo fails when player has not drawn yet`() = runTest {
        val fixtures = Fixtures()
        val winner = FakeMahjongPlayerFactory.create(id = winnerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(winner), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, winnerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(winnerId, gameId, GameAction.Tsumo), result.error)
    }

    /**
     * 驗證手牌根本沒有胡牌時回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test declare tsumo fails when hand is not actually a winning hand`() = runTest {
        val fixtures = Fixtures()
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = winningTile),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(winner), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, winnerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(winnerId, gameId, GameAction.Tsumo), result.error)
    }

    /**
     * 驗證不支援自摸結算的規則不會讓自摸宣告成立。
     */
    @Test
    fun `test declare tsumo fails for a ruleset without tsumo support`() = runTest {
        val fixtures = Fixtures()
        val winner = FakeMahjongPlayerFactory.create(id = winnerId, initialSeat = Wind.EAST, hand = daisangenHand())
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(winner), config = TaiwanRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, winnerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(winnerId, gameId, GameAction.Tsumo), result.error)
    }
}
