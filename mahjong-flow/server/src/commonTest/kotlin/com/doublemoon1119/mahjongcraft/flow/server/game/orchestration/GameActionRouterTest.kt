package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKyuushuKyuuhaiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToChankanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
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
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [GameActionRouter] 的單元測試類別。
 *
 * 不重新驗證各 use case 自己的業務邏輯（那些已經有各自的測試檔案），只驗證路由本身接對了——
 * 每個 [GameCommand] 變體確實被轉呼叫到正確的 use case、正確的參數位置。
 */
class GameActionRouterTest {

    private val gameId = Uuid.random()
    private val playerId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val eventPublisher = FakeGameEventPublisher()
        val router = GameActionRouter(
            drawTileUseCase = DrawTileUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            discardTileUseCase = DiscardTileUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            declareRiichiUseCase = DeclareRiichiUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            declareTsumoUseCase = DeclareTsumoUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            declareKanUseCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            respondToDiscardUseCase = RespondToDiscardUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            respondToChankanUseCase = RespondToChankanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            declareKyuushuKyuuhaiUseCase = DeclareKyuushuKyuuhaiUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
        )
    }

    /**
     * 驗證 [GameCommand.Draw] 成功路由到 [DrawTileUseCase]：牌山正確縮減、手牌正確補上摸到的牌。
     */
    @Test
    fun `test draw command routes to DrawTileUseCase and succeeds`() = runTest {
        val fixtures = Fixtures()
        val drawnTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(drawnTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.router(gameId, playerId, GameCommand.Draw)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(drawnTile, newState.players.first { it.id == playerId }.hand.lastDrawn)
        assertEquals(0, newState.tileWall.remainingCount)
    }

    /**
     * 驗證 [GameCommand.Draw] 在已摸牌時失敗，且錯誤攜帶的 [GameAction] 正是
     * [DrawTileUseCase] 會建構的 `GameAction.Draw`——證明確實路由到 [DrawTileUseCase]，
     * 而非其他無參數的 use case（例如 [GameCommand.Tsumo] 對應的 [DeclareTsumoUseCase]）。
     */
    @Test
    fun `test draw command fails with GameAction Draw payload when already drawn`() = runTest {
        val fixtures = Fixtures()
        val alreadyDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = alreadyDrawn))
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.router(gameId, playerId, GameCommand.Draw)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(playerId, gameId, GameAction.Draw), result.error)
    }

    /**
     * 驗證 [GameCommand.Discard] 路由到 [DiscardTileUseCase]，錯誤攜帶的 [GameAction] 含正確的 `tileId`。
     */
    @Test
    fun `test discard command routes to DiscardTileUseCase`() = runTest {
        val fixtures = Fixtures()
        val tileId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.router(gameId, playerId, GameCommand.Discard(tileId))

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(playerId, gameId, GameAction.Discard(tileId)), result.error)
    }

    /**
     * 驗證 [GameCommand.Riichi] 路由到 [DeclareRiichiUseCase]，錯誤攜帶的 [GameAction] 正是
     * `GameAction.Riichi`（不像 [GameCommand.Discard] 一樣攜帶 `tileId`，證明確實傳給了
     * [DeclareRiichiUseCase] 而非 [DiscardTileUseCase]）。
     */
    @Test
    fun `test riichi command routes to DeclareRiichiUseCase`() = runTest {
        val fixtures = Fixtures()
        val tileId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.router(gameId, playerId, GameCommand.Riichi(tileId))

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(playerId, gameId, GameAction.Riichi), result.error)
    }

    /**
     * 驗證 [GameCommand.Tsumo] 路由到 [DeclareTsumoUseCase]，錯誤攜帶的 [GameAction] 正是
     * `GameAction.Tsumo`。
     */
    @Test
    fun `test tsumo command routes to DeclareTsumoUseCase`() = runTest {
        val fixtures = Fixtures()
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.router(gameId, playerId, GameCommand.Tsumo)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(playerId, gameId, GameAction.Tsumo), result.error)
    }

    /**
     * 驗證 [GameCommand.Kan] 路由到 [DeclareKanUseCase]，錯誤攜帶的 [GameAction] 含正確的
     * `type`/`tileId`。
     */
    @Test
    fun `test kan command routes to DeclareKanUseCase`() = runTest {
        val fixtures = Fixtures()
        val tileId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.router(gameId, playerId, GameCommand.Kan(GameAction.KanType.CLOSED_KAN, tileId))

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(playerId, gameId, GameAction.Kan(GameAction.KanType.CLOSED_KAN, tileId, emptyList())),
            result.error,
        )
    }

    /**
     * 驗證 [GameCommand.KyuushuKyuuhai] 路由到 [DeclareKyuushuKyuuhaiUseCase]：這是唯一在
     * 尚未摸牌時會回傳 [GameError.UnsupportedAction]（而非 `IllegalAction`）的命令，
     * 這個獨特的錯誤型別本身就能證明路由正確。
     */
    @Test
    fun `test kyuushu kyuuhai command routes to DeclareKyuushuKyuuhaiUseCase`() = runTest {
        val fixtures = Fixtures()
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.router(gameId, playerId, GameCommand.KyuushuKyuuhai)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.UnsupportedAction(gameId, playerId), result.error)
    }

    private fun discardReactionTable(discarderId: Uuid, respondentId: Uuid): TableState {
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
        return FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, respondent),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedSouthTile.id, setOf(respondentId)),
        )
    }

    private fun chankanTable(declarerId: Uuid, robberId: Uuid, tileWall: TileWall): TableState {
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
        return FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber),
            config = RiichiRuleConfig(),
            tileWall = tileWall,
            currentPlayerIndex = 0,
            pendingChankan = PendingChankanReaction(declarerId, kanAction, robbedWhiteTile, setOf(robberId)),
        )
    }

    /**
     * 驗證 [GameCommand.RespondToDiscard] 路由到 [RespondToDiscardUseCase]：對只開了
     * `pendingReaction`（未開 `pendingChankan`）的桌況送出，應成功結算（全員過牌 → 反應視窗清除）。
     */
    @Test
    fun `test respond to discard command routes to RespondToDiscardUseCase and succeeds`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        fixtures.gameRepo.setTableState(discardReactionTable(discarderId, respondentId))

        val result = fixtures.router(gameId, respondentId, GameCommand.RespondToDiscard(GameAction.Pass))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        assertNull(fixtures.gameRepo.getTableState(gameId)!!.pendingReaction)
    }

    /**
     * 驗證 [GameCommand.RespondToChankan] 不會被誤路由到 [RespondToDiscardUseCase]：對只開了
     * `pendingReaction` 的桌況送出 [GameCommand.RespondToChankan]，因為 `pendingChankan == null`
     * 應失敗。
     */
    @Test
    fun `test respond to chankan command fails when only a discard reaction window is pending`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        fixtures.gameRepo.setTableState(discardReactionTable(discarderId, respondentId))

        val result = fixtures.router(gameId, respondentId, GameCommand.RespondToChankan(GameAction.Pass))

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(respondentId, gameId, GameAction.Pass), result.error)
    }

    /**
     * 驗證 [GameCommand.RespondToChankan] 路由到 [RespondToChankanUseCase]：對只開了
     * `pendingChankan` 的桌況送出，應成功結算（全員過牌 → 補做套用副露與嶺上摸牌）。
     */
    @Test
    fun `test respond to chankan command routes to RespondToChankanUseCase and succeeds`() = runTest {
        val fixtures = Fixtures()
        val declarerId = Uuid.random()
        val robberId = Uuid.random()
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        fixtures.gameRepo.setTableState(chankanTable(declarerId, robberId, TileWall(listOf(rinshanTile))))

        val result = fixtures.router(gameId, robberId, GameCommand.RespondToChankan(GameAction.Pass))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        assertNull(fixtures.gameRepo.getTableState(gameId)!!.pendingChankan)
    }

    /**
     * 驗證 [GameCommand.RespondToDiscard] 不會被誤路由到 [RespondToChankanUseCase]：對只開了
     * `pendingChankan` 的桌況送出 [GameCommand.RespondToDiscard]，因為 `pendingReaction == null`
     * 應失敗。
     */
    @Test
    fun `test respond to discard command fails when only a chankan window is pending`() = runTest {
        val fixtures = Fixtures()
        val declarerId = Uuid.random()
        val robberId = Uuid.random()
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        fixtures.gameRepo.setTableState(chankanTable(declarerId, robberId, TileWall(listOf(rinshanTile))))

        val result = fixtures.router(gameId, robberId, GameCommand.RespondToDiscard(GameAction.Pass))

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(robberId, gameId, GameAction.Pass), result.error)
    }

    /**
     * 驗證對局不存在時，任一命令皆回傳 [GameError.GameNotFound]（證明 `gameId` 有正確往下傳）。
     */
    @Test
    fun `test any command fails with GameNotFound when game does not exist`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.router(gameId, playerId, GameCommand.Draw)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }
}
