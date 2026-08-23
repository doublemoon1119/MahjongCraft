package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingChankanReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [RespondToChankanUseCase] 的單元測試類別。
 *
 * 驗證搶槓反應視窗的回應：只信任 Ron/Pass（過濾掉 getLegalActions 誤算出的吃/碰/明槓資格）、
 * 搶槓成功時透過 [RonSettlementResolver] 結算（暗槓/加槓宣告視為未成立，副露不套用）、全員放過時
 * 透過 [KanDeclarationApplier] 補做原本被暫緩的套用，以及各種驗證失敗案例。
 */
class RespondToChankanUseCaseTest {

    private val gameId = Uuid.random()
    private val declarerId = Uuid.random()
    private val robberId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val useCase = RespondToChankanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher)
    }

    private val whiteTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
    private val whiteTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
    private val whiteTile3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
    private val robbedWhiteTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
    private val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
    private val kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, robbedWhiteTile.id, emptyList())

    private fun existingPon() = Meld(
        MeldType.PON,
        listOf(whiteTile1, whiteTile2, whiteTile3),
        sourceTile = whiteTile3,
        sourceDirection = RelativeDirection.Left,
    )

    // 役牌發已成立（1 翻）、單騎聽白（搶槓的那張牌）
    private fun robberHand() = Hand(
        tiles = listOf(
            Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
            Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3), Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Dot, 5), Tile.Numeric(Tile.Suit.Dot, 6), Tile.Numeric(Tile.Suit.Dot, 7),
            Tile.Numeric(Tile.Suit.Bamboo, 6), Tile.Numeric(Tile.Suit.Bamboo, 7), Tile.Numeric(Tile.Suit.Bamboo, 8),
        ).map { FakeIdentifiedTileFactory.create(it) } + FakeIdentifiedTileFactory.create(Tile.Honor.White),
    )

    private fun setUpTable(initialDeadWall: List<IdentifiedTile> = listOf(rinshanTile)): TableState {
        val declarer = FakeMahjongPlayerFactory.create(
            id = declarerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon()), lastDrawn = robbedWhiteTile),
        )
        val robber = FakeMahjongPlayerFactory.create(
            id = robberId,
            initialSeat = Wind.SOUTH,
            hand = robberHand(),
            playerRuleState = RiichiPlayerState(),
        )
        return FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber),
            config = RiichiRuleConfig(),
            initialDeadWall = initialDeadWall,
            currentPlayerIndex = 0,
            pendingChankan = PendingChankanReaction(declarerId, kanAction, robbedWhiteTile, setOf(robberId)),
        )
    }

    /**
     * 驗證搶槓成功：贏家分數增加（含搶槓 1 翻的加成）、`actionHistory` 記錄 `Ron`、反應視窗清除，
     * 且暗槓/加槓宣告視為未成立——宣告者的副露維持原本的 PON、`lastDrawn` 不變、牌山不縮減。
     */
    @Test
    fun `test chankan ron settles winner and leaves the kan unapplied`() = runTest {
        val fixtures = Fixtures()
        fixtures.gameRepo.setTableState(setUpTable())

        val result = fixtures.useCase(gameId, robberId, GameAction.Ron(robbedWhiteTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(newState.pendingChankan)

        val winner = newState.players.first { it.id == robberId }
        assertTrue(winner.score > 0, "Winner should have gained points.")
        assertEquals(GameAction.Ron(robbedWhiteTile.id), winner.actionHistory.last())

        val declarer = newState.players.first { it.id == declarerId }
        assertTrue(declarer.score < 0, "The declarer should pay for the robbed kan, like a discarder.")
        assertEquals(MeldType.PON, declarer.hand.melds.single().type, "The kan must not be applied; it was robbed.")
        assertEquals(robbedWhiteTile, declarer.hand.lastDrawn, "The declarer's hand should remain untouched.")

        assertEquals(
            listOf(GameAction.Ron(robbedWhiteTile.id)),
            fixtures.eventPublisher.getNotifiedActions(gameId, declarerId, robberId),
            "Only the Ron response should be broadcast; no rinshan draw happened.",
        )
        assertNull(
            fixtures.presentationPublisher.getPublishedPlayerArea(gameId),
            "Robbing the kan is a Ron, not a completed kan — no rinshan tile was drawn, so nothing should be presented as drawn.",
        )

        val celebrations = fixtures.presentationPublisher.getPublishedWinCelebrations(gameId)
        assertEquals(1, celebrations.size)
        assertEquals(newState.players.indexOfFirst { it.id == robberId }, celebrations.single().winnerSeatIndex)
        assertEquals(robbedWhiteTile.id, celebrations.single().winningTileId)
        assertFalse(celebrations.single().isTsumo)
    }

    /**
     * 驗證全員放過時：原本被 `DeclareKanUseCase` 暫緩的副露套用與嶺上摸牌會在這裡補做——副露升級為
     * ADDED_KAN、`lastDrawn` 為補摸的嶺上牌、`actionHistory` 依序記錄 `Kan` → `Draw`、反應視窗清除、
     * 依序廣播 `Pass` → `Draw`。
     */
    @Test
    fun `test all pass resumes the kan declaration`() = runTest {
        val fixtures = Fixtures()
        fixtures.gameRepo.setTableState(setUpTable())

        val result = fixtures.useCase(gameId, robberId, GameAction.Pass)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(newState.pendingChankan)

        val declarer = newState.players.first { it.id == declarerId }
        val meld = declarer.hand.melds.single()
        assertEquals(MeldType.ADDED_KAN, meld.type)
        assertEquals(setOf(whiteTile1, whiteTile2, whiteTile3, robbedWhiteTile), meld.tiles.toSet())
        assertEquals(rinshanTile, declarer.hand.lastDrawn)
        assertEquals(listOf(kanAction, GameAction.Draw), declarer.actionHistory.takeLast(2))

        assertEquals(
            listOf(GameAction.Pass, GameAction.Draw),
            fixtures.eventPublisher.getNotifiedActions(gameId, robberId, robberId),
        )
        assertEquals(
            rinshanTile.id,
            fixtures.presentationPublisher.getPublishedPlayerArea(gameId)?.drawnTileId,
            "The rinshan tile drawn once the kan actually goes through should be presented as a drawn tile.",
        )
        assertTrue(
            fixtures.presentationPublisher.getPublishedWinCelebrations(gameId).isEmpty(),
            "A completed kan (not robbed) should never trigger a win celebration.",
        )
    }

    /**
     * 驗證全員放過、但牌山恰好在補摸嶺上牌時摸盡：回傳 [GameError.WallExhausted]，且桌況維持回應前
     * 的樣子不變（all-or-nothing）。
     */
    @Test
    fun `test all pass fails with wall exhausted and applies nothing`() = runTest {
        val fixtures = Fixtures()
        fixtures.gameRepo.setTableState(setUpTable(initialDeadWall = emptyList()))

        val result = fixtures.useCase(gameId, robberId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.WallExhausted(gameId), result.error)
        val unchangedState = fixtures.gameRepo.getTableState(gameId)!!
        val unchangedDeclarer = unchangedState.players.first { it.id == declarerId }
        assertEquals(MeldType.PON, unchangedDeclarer.hand.melds.single().type, "The meld should not be applied.")
        assertEquals(robbedWhiteTile, unchangedDeclarer.hand.lastDrawn)
    }

    /**
     * 驗證回應不在 Ron/Pass 之列時回傳 [GameError.IllegalAction]——即使 `getLegalActions` 的
     * 「反應」分支誤算出其他資格（例如碰），搶槓情境下這些都不合法。
     */
    @Test
    fun `test respond fails for actions other than ron or pass`() = runTest {
        val fixtures = Fixtures()
        fixtures.gameRepo.setTableState(setUpTable())

        val result = fixtures.useCase(gameId, robberId, GameAction.Pon(robbedWhiteTile.id))

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(robberId, gameId, GameAction.Pon(robbedWhiteTile.id)), result.error)
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test respond fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId, robberId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }

    /**
     * 驗證發起請求的玩家不在該對局中時回傳 [GameError.PlayerNotInGame]。
     */
    @Test
    fun `test respond fails when player not in game`() = runTest {
        val fixtures = Fixtures()
        fixtures.gameRepo.setTableState(setUpTable())
        val strangerId = Uuid.random()

        val result = fixtures.useCase(gameId, strangerId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.PlayerNotInGame(strangerId, gameId), result.error)
    }

    /**
     * 驗證目前沒有搶槓反應視窗時回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test respond fails when there is no pending chankan`() = runTest {
        val fixtures = Fixtures()
        val table = setUpTable().copy(pendingChankan = null)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, robberId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(robberId, gameId, GameAction.Pass), result.error)
    }

    /**
     * 驗證玩家不在本次反應視窗的資格名單中時回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test respond fails when player is not eligible`() = runTest {
        val fixtures = Fixtures()
        val bystanderId = Uuid.random()
        val bystander = FakeMahjongPlayerFactory.create(id = bystanderId, initialSeat = Wind.WEST)
        val table = setUpTable().let { it.copy(players = it.players + bystander) }
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, bystanderId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(bystanderId, gameId, GameAction.Pass), result.error)
    }

    /**
     * 驗證玩家已經回應過一次後，不能再次回應。
     */
    @Test
    fun `test respond fails when player has already responded`() = runTest {
        val fixtures = Fixtures()
        val declarer = FakeMahjongPlayerFactory.create(
            id = declarerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon()), lastDrawn = robbedWhiteTile),
        )
        val robber = FakeMahjongPlayerFactory.create(id = robberId, initialSeat = Wind.SOUTH, hand = robberHand())
        val otherEligibleId = Uuid.random()
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber),
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(rinshanTile)),
            currentPlayerIndex = 0,
            pendingChankan = PendingChankanReaction(
                declarerId,
                kanAction,
                robbedWhiteTile,
                setOf(robberId, otherEligibleId),
                responses = mapOf(robberId to GameAction.Pass),
            ),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, robberId, GameAction.Pass)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(robberId, gameId, GameAction.Pass), result.error)
    }
}
