package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingChankanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [GetLegalActionsUseCase] 的單元測試類別。
 *
 * 驗證四種情境的判斷與參數組裝：搶槓反應（過濾只留 Ron/Pass）、捨牌反應（不過濾）、
 * 自己回合已摸牌（需合併立直資格與 Tsumo/Kan/KyuushuKyuuhai 資格兩次查詢），以及其餘情況回傳空清單。
 */
class GetLegalActionsUseCaseTest {

    private val gameId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val useCase = GetLegalActionsUseCase(gameRepo, moduleRegistry)
    }

    /**
     * 驗證自己回合已摸牌時，立直資格（`incomingTile = null` 查詢）與暗槓資格
     * （`incomingTile = 剝離後的 lastDrawn` 查詢）皆會出現在合併後的清單，證明兩次查詢確實合併，
     * 不是只取其中一次。
     *
     * 手牌設計：東東東（暗槓候選，摸到第 4 張東）+ 234萬 + 567萬 + 789筒 + 北（單騎聽）。
     * 捨棄新摸到的東可回到原本的聽牌形（單騎聽北），故立直合法；同時原手牌中已有 3 張東，
     * 加上新摸到的第 4 張東，暗槓資格成立。刻意選擇字牌（東）作為暗槓候選、且聽牌張（北）與
     * 東無關聯，避免摸到第 4 張東後意外湊成自摸（若把多出的東當作對子，剩下 2 張東無法單獨成面子）。
     */
    @Test
    fun `test own turn after draw merges riichi and kan eligibility`() = runTest {
        val fixtures = Fixtures()
        val east1 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east2 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east3 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east4 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val runTiles = listOf(2, 3, 4, 5, 6, 7).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, it)) }
        val dotTiles = listOf(7, 8, 9).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, it)) }
        val tankiWaitTile = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val standingTiles = listOf(east1, east2, east3) + runTiles + dotTiles + listOf(tankiWaitTile)

        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = standingTiles, lastDrawn = east4),
            playerRuleState = RiichiPlayerState(),
        ).copy(score = 25000)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val actions = (result as Outcome.Success).value
        assertTrue(actions.any { it is GameAction.Riichi }, "Riichi should be legal from the incomingTile=null query.")
        assertTrue(
            actions.any { it is GameAction.Kan && it.type == GameAction.KanType.CLOSED_KAN },
            "Closed kan should be legal from the incomingTile=lastDrawn query.",
        )
    }

    /**
     * 驗證自己回合但尚未摸牌（`lastDrawn == null`）時回傳空清單。
     */
    @Test
    fun `test own turn but not yet drawn returns empty list`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId)

        assertTrue(result is Outcome.Success)
        assertEquals(emptyList(), (result as Outcome.Success).value)
    }

    /**
     * 驗證有搶槓反應資格、且尚未回應時：清單只含 [GameAction.Ron]/[GameAction.Pass]，即使
     * `getLegalActions` 的「反應」分支會一併算出吃/碰/明槓資格，這裡也必須過濾掉。
     */
    @Test
    fun `test pending chankan eligibility filters to ron and pass only`() = runTest {
        val fixtures = Fixtures()
        val declarerId = Uuid.random()
        val robberId = Uuid.random()
        val whiteTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val whiteTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val whiteTile3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val robbedWhiteTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val existingPon = Meld(MeldType.PON, listOf(whiteTile1, whiteTile2, whiteTile3), sourceTile = whiteTile3, sourceDirection = RelativeDirection.Left)
        val kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, robbedWhiteTile.id, emptyList())
        val declarer = FakeMahjongPlayerFactory.create(
            id = declarerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon), lastDrawn = robbedWhiteTile),
        )
        // 役牌發已成立（1 翻）、單騎聽白（搶槓的那張牌）
        val robberHand = Hand(
            tiles = listOf(
                Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
                Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3), Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Dot, 5), Tile.Numeric(Tile.Suit.Dot, 6), Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Bamboo, 6), Tile.Numeric(Tile.Suit.Bamboo, 7), Tile.Numeric(Tile.Suit.Bamboo, 8),
            ).map { FakeIdentifiedTileFactory.create(it) } + FakeIdentifiedTileFactory.create(Tile.Honor.White),
        )
        val robber = FakeMahjongPlayerFactory.create(id = robberId, initialSeat = Wind.SOUTH, hand = robberHand, playerRuleState = RiichiPlayerState())
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingChankan = PendingChankanReaction(declarerId, kanAction, robbedWhiteTile, setOf(robberId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, robberId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        assertEquals(
            listOf(GameAction.Ron(robbedWhiteTile.id), GameAction.Pass),
            (result as Outcome.Success).value,
        )
    }

    /**
     * 驗證已經回應過搶槓視窗的玩家再次查詢時回傳空清單。
     */
    @Test
    fun `test pending chankan already responded returns empty list`() = runTest {
        val fixtures = Fixtures()
        val declarerId = Uuid.random()
        val robberId = Uuid.random()
        val robbedWhiteTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, robbedWhiteTile.id, emptyList())
        val declarer = FakeMahjongPlayerFactory.create(id = declarerId, initialSeat = Wind.EAST)
        val robber = FakeMahjongPlayerFactory.create(id = robberId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingChankan = PendingChankanReaction(
                declarerId,
                kanAction,
                robbedWhiteTile,
                setOf(robberId),
                responses = mapOf(robberId to GameAction.Pass),
            ),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, robberId)

        assertTrue(result is Outcome.Success)
        assertEquals(emptyList(), (result as Outcome.Success).value)
    }

    /**
     * 驗證有捨牌反應資格、且尚未回應時：清單不過濾，碰資格會照常出現
     * （與搶槓情境的過濾行為相反）。
     */
    @Test
    fun `test pending discard reaction eligibility returns unfiltered legal actions`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val discardedSouthTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedSouthTile),
        )
        val southTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val southTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val respondent = FakeMahjongPlayerFactory.create(
            id = respondentId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(southTile1, southTile2)),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, respondent),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedSouthTile.id, setOf(respondentId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, respondentId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        assertEquals(
            listOf(GameAction.Pon(discardedSouthTile.id), GameAction.Pass),
            (result as Outcome.Success).value,
        )
    }

    /**
     * 驗證已經回應過捨牌反應視窗的玩家再次查詢時回傳空清單。
     */
    @Test
    fun `test pending discard reaction already responded returns empty list`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val discardedSouthTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedSouthTile),
        )
        val respondent = FakeMahjongPlayerFactory.create(id = respondentId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, respondent),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(
                discarderId,
                discardedSouthTile.id,
                setOf(respondentId),
                responses = mapOf(respondentId to GameAction.Pass),
            ),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, respondentId)

        assertTrue(result is Outcome.Success)
        assertEquals(emptyList(), (result as Outcome.Success).value)
    }

    /**
     * 驗證不是自己回合、也沒有任何反應資格時回傳空清單（旁觀者）。
     */
    @Test
    fun `test bystander with no eligibility returns empty list`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val bystanderId = Uuid.random()
        val bystander = FakeMahjongPlayerFactory.create(id = bystanderId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, bystander),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, bystanderId)

        assertTrue(result is Outcome.Success)
        assertEquals(emptyList(), (result as Outcome.Success).value)
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test get legal actions fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId, Uuid.random())

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }

    /**
     * 驗證發起查詢的玩家不在該對局中時回傳 [GameError.PlayerNotInGame]。
     */
    @Test
    fun `test get legal actions fails when player not in game`() = runTest {
        val fixtures = Fixtures()
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)
        val strangerId = Uuid.random()

        val result = fixtures.useCase(gameId, strangerId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.PlayerNotInGame(strangerId, gameId), result.error)
    }
}
