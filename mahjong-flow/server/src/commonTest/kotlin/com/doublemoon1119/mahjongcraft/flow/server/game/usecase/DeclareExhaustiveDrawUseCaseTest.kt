package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [DeclareExhaustiveDrawUseCase] 的單元測試類別。
 *
 * 驗證一般流局（牌山摸盡）的不聽罰符結算、`ExhaustiveDraw` 只記錄進聽牌玩家的
 * `actionHistory`、快照與事件的同步行為，以及對局不存在的失敗案例。
 */
class DeclareExhaustiveDrawUseCaseTest {

    /** 受測對局識別碼。 */
    private val gameId = Uuid.random()

    /**
     * 一般流局結算共用的測試環境。
     *
     * @property gameRepo 保存權威對局的測試倉庫。
     * @property moduleRegistry 已註冊內建規則的中心。
     * @property snapshotRepo 保存玩家可見快照的替身。
     * @property snapshotSynchronizer 更新快照的元件。
     * @property eventPublisher 記錄動作通知的替身。
     * @property useCase 受測流局用例。
     */
    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl(moduleRegistry))
        val eventPublisher = FakeGameEventPublisher()
        val useCase = DeclareExhaustiveDrawUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher)
    }

    /** 驗證排定的一般流局結算在同一筆寫入中被消耗，重複接續也不會再結算一次。 */
    @Test
    fun `test scheduled exhaustive draw is cleared by the settlement`() = runTest {
        val fixtures = Fixtures()
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(FakeMahjongPlayerFactory.create(hand = tenpaiHand())) + List(3) { FakeMahjongPlayerFactory.create(hand = notTenpaiHand()) },
            config = RiichiRuleConfig(),
        )
        fixtures.gameRepo.setGame(Game(tableState = table, flowConfig = GameFlowConfig(), pendingTransition = PendingGameTransition.SettleExhaustiveDraw))

        assertIs<Outcome.Success<*>>(fixtures.useCase(gameId))

        val game = assertNotNull(fixtures.gameRepo.getGame(gameId))
        assertNull(game.pendingTransition)
        assertNotNull(game.roundCompletion)
    }

    // 十三張萬子立牌 1112345678999m；加入任一種萬子都能和牌。
    private fun tenpaiHand() = FakeHandFactory.create(
        listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 2),
            Tile.Numeric(Tile.Suit.Character, 3),
            Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Character, 5),
            Tile.Numeric(Tile.Suit.Character, 6),
            Tile.Numeric(Tile.Suit.Character, 7),
            Tile.Numeric(Tile.Suit.Character, 8),
            Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Character, 9),
        ),
    )

    // 三種數牌各取 1、4、7，再加四種風牌，建立未聽牌的十三張立牌。
    private fun notTenpaiHand() = FakeHandFactory.create(
        listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Character, 7),
            Tile.Numeric(Tile.Suit.Dot, 1),
            Tile.Numeric(Tile.Suit.Dot, 4),
            Tile.Numeric(Tile.Suit.Dot, 7),
            Tile.Numeric(Tile.Suit.Bamboo, 1),
            Tile.Numeric(Tile.Suit.Bamboo, 4),
            Tile.Numeric(Tile.Suit.Bamboo, 7),
            Tile.Honor.East,
            Tile.Honor.South,
            Tile.Honor.West,
            Tile.Honor.North,
        ),
    )

    // 全為么九牌、皆未被鳴走的牌河，成立流局滿貫的必要條件
    private fun allYaochuuDiscardPile() = RiichiDiscardPile()
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)))

    /**
     * 驗證恰好一人聽牌時，聽牌者收下不聽罰符（+3000），其餘三家各付 1000，
     * 且只有聽牌者的 `actionHistory` 記錄 `ExhaustiveDraw`。
     */
    @Test
    fun `test declare exhaustive draw settles noten penalty and records ExhaustiveDraw only for tenpai player`() = runTest {
        val fixtures = Fixtures()
        val tenpaiPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, hand = tenpaiHand()).copy(score = 25000)
        val notenPlayers = listOf(Wind.SOUTH, Wind.WEST, Wind.NORTH).map {
            FakeMahjongPlayerFactory.create(initialSeat = it, hand = notTenpaiHand()).copy(score = 25000)
        }
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(tenpaiPlayer) + notenPlayers,
            config = RiichiRuleConfig(),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        assertEquals(RiichiExhaustiveDrawReason.Normal, assertIs<Outcome.Success<DeclaredExhaustiveDraw>>(result).value.settlement.reason)
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(25000 + 3000, newState.players.first { it.id == tenpaiPlayer.id }.score)
        notenPlayers.forEach { player ->
            assertEquals(25000 - 1000, newState.players.first { it.id == player.id }.score)
        }
        assertEquals(GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.Normal), newState.players.first { it.id == tenpaiPlayer.id }.actionHistory.last())
        notenPlayers.forEach { player ->
            assertTrue(newState.players.first { it.id == player.id }.actionHistory.isEmpty(), "Noten players should not have ExhaustiveDraw recorded.")
        }
    }

    /**
     * 驗證一般流局 use case 不再處理流局滿貫；該結果必須由 post-reaction outcome resolver 先攔截。
     */
    @Test
    fun `test declare exhaustive draw does not settle nagashi mangan`() = runTest {
        val fixtures = Fixtures()
        val achiever = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = notTenpaiHand(),
            discardPile = allYaochuuDiscardPile(),
        ).copy(score = 25000)
        val others = listOf(Wind.SOUTH, Wind.WEST, Wind.NORTH).map {
            FakeMahjongPlayerFactory.create(initialSeat = it, hand = notTenpaiHand()).copy(score = 25000)
        }
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(achiever) + others,
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 2),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(25000, newState.players.first { it.id == achiever.id }.score)
        others.forEach { player ->
            assertEquals(25000, newState.players.first { it.id == player.id }.score)
        }
        assertEquals(2, (newState.dynamicRuleState as RiichiDynamicState).riichiStickCount)
    }

    /**
     * 驗證即使多位玩家的牌河符合流局滿貫，一般流局 use case 也不會越權結算或收取供託。
     */
    @Test
    fun `test declare exhaustive draw ignores multiple nagashi mangan candidates`() = runTest {
        val fixtures = Fixtures()
        // 讓莊家與南家都符合牌河條件，確認本用例不替流局滿貫進行結算。
        val dealerAchiever = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = notTenpaiHand(),
            discardPile = allYaochuuDiscardPile(),
        ).copy(score = 25000)
        val otherAchiever = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = notTenpaiHand(),
            discardPile = allYaochuuDiscardPile(),
        ).copy(score = 25000)
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST, hand = notTenpaiHand()).copy(score = 25000)
        val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH, hand = notTenpaiHand()).copy(score = 25000)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealerAchiever, otherAchiever, west, north),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 2),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        newState.players.forEach { assertEquals(25000, it.score) }
        assertEquals(2, (newState.dynamicRuleState as RiichiDynamicState).riichiStickCount)
    }

    /**
     * 驗證無人聽牌、無人流局滿貫時，不進行任何點數交換，也不記錄任何 `ExhaustiveDraw`。
     */
    @Test
    fun `test declare exhaustive draw does not exchange points when nobody is tenpai`() = runTest {
        val fixtures = Fixtures()
        val players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map {
            FakeMahjongPlayerFactory.create(initialSeat = it, hand = notTenpaiHand()).copy(score = 25000)
        }
        val table = FakeTableStateFactory.create(id = gameId, players = players, config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        newState.players.forEach { player ->
            assertEquals(25000, player.score)
            assertTrue(player.actionHistory.isEmpty())
        }
    }

    /**
     * 驗證流局結算後所有觀察者的快照皆同步更新，且所有玩家皆收到 `ExhaustiveDraw` 事件通知。
     */
    @Test
    fun `test declare exhaustive draw syncs snapshot and notifies all players`() = runTest {
        val fixtures = Fixtures()
        val dealer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, hand = tenpaiHand())
        val southId = Uuid.random()
        val south = FakeMahjongPlayerFactory.create(id = southId, initialSeat = Wind.SOUTH, hand = notTenpaiHand())
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(dealer, south), config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)
        fixtures.snapshotRepo.setSnapshot(dealer.id, table.toSnapshot(setOf(dealer.id), setAsideTiles = { emptyList() }))
        fixtures.snapshotRepo.setSnapshot(southId, table.toSnapshot(setOf(southId), setAsideTiles = { emptyList() }))

        fixtures.useCase(gameId)

        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, dealer.id))
        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, southId))
        val expectedAction = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.Normal)
        assertEquals(expectedAction, fixtures.eventPublisher.getNotifiedAction(gameId, dealer.id, dealer.id))
        assertEquals(expectedAction, fixtures.eventPublisher.getNotifiedAction(gameId, southId, dealer.id))
    }

    /** 一般流局後聽牌者的手牌對其他觀察者公開，不聽者的手牌仍然不公開。 */
    @Test
    fun `test declare exhaustive draw reveals tenpai hands but not noten hands`() = runTest {
        val fixtures = Fixtures()
        val dealer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, hand = tenpaiHand())
        val south = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH, hand = notTenpaiHand())
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(dealer, south), config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)
        listOf(dealer, south).forEach { player ->
            fixtures.snapshotRepo.setSnapshot(player.id, table.toSnapshot(setOf(player.id), setAsideTiles = { emptyList() }))
        }

        fixtures.useCase(gameId)

        val dealerTileIds = dealer.hand.standingTiles.map { it.id }.toSet()
        assertEquals(dealerTileIds, fixtures.gameRepo.getTableState(gameId)!!.revealedHandTileIds)
        val southView = assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, south.id))
        assertEquals(dealerTileIds, southView.revealedHandTiles.map { it.id }.toSet())
        val dealerView = assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, dealer.id))
        assertEquals(dealerTileIds, dealerView.revealedHandTiles.map { it.id }.toSet())
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test declare exhaustive draw fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }
}
