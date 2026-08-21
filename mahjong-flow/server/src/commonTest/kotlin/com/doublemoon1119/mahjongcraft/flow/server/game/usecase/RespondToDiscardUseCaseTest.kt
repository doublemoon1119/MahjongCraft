package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoLiability
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoYaku
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
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
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val handSortPreferenceStore = HandSortPreferenceStore()
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val useCase = RespondToDiscardUseCase(
            gameRepo,
            moduleRegistry,
            snapshotSynchronizer,
            handSortPreferenceStore,
            eventPublisher,
            presentationPublisher,
        )
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
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(FakeIdentifiedTileFactory.create(Tile.Honor.White), FakeIdentifiedTileFactory.create(Tile.Honor.White))),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
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
            "Passing on an available Pon should be recorded as a temporary pass (過水碰).",
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
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val handTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val handTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(handTile1, handTile2)),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
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
     * 驗證碰牌得標後，即使沒有新增捨牌，也會觸發平台呈現層重新呈現丟牌者的牌河——因為
     * `takeLast()` 改變了 `isTaken` 狀態，側身標記可能因此位移。
     */
    @Test
    fun `test pon success publishes discarder's discard pile presentation`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val handTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val handTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(handTile1, handTile2)),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.useCase(gameId, responderId, GameAction.Pon(discardedTile.id))

        val publishedDiscardPile = fixtures.presentationPublisher.getPublishedDiscardPile(gameId)
        assertNotNull(publishedDiscardPile)
        assertEquals(0, publishedDiscardPile.seatIndex)
        assertEquals(
            emptyList(),
            publishedDiscardPile.discardTileIds,
            "The claimed tile is now taken and moves to the meld area, so it's excluded from the river presentation.",
        )
        assertEquals(null, publishedDiscardPile.sidewaysMarkedTileId, "FakeDiscardPile has no riichi concept, so no tile should be marked sideways.")
    }

    /**
     * 驗證碰牌得標後，觸發平台呈現層重新呈現得標玩家的整份副露列表。
     */
    @Test
    fun `test pon success publishes winner's melds presentation`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val handTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val handTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(handTile1, handTile2)),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.useCase(gameId, responderId, GameAction.Pon(discardedTile.id))

        val publishedMelds = fixtures.presentationPublisher.getPublishedPlayerArea(gameId)
        assertNotNull(publishedMelds)
        assertEquals(1, publishedMelds.seatIndex, "The winner (responder) sits at seat index 1.")
        val meld = publishedMelds.melds.single()
        assertEquals(MeldType.PON, meld.type)
        assertEquals(setOf(handTile1.id, handTile2.id, discardedTile.id), meld.tileIds.toSet())
        assertEquals(discardedTile.id, meld.calledTileId)
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
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val handTile4 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4))
        val handTile6 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6))
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(handTile4, handTile6)),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
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
     * 驗證明槓（大明槓）得標後：正確套用副露、標記捨牌已被鳴走、且立即從死牌區補摸嶺上牌
     * （取代過去依賴得標玩家事後另外呼叫 `DrawTileUseCase`、從牌山前端摸錯位置的既有缺口）——
     * `lastDrawn` 為嶺上牌、`actionHistory` 依序記錄 `Kan` → `Draw`、牌山正確縮減 1 張。
     */
    @Test
    fun `test open kan success applies meld and draws replacement tile from dead wall`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val handTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val handTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val handTile3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(handTile1, handTile2, handTile3)),
        )
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(rinshanTile)),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val kanAction = GameAction.Kan(GameAction.KanType.OPEN_KAN, discardedTile.id, listOf(handTile1.id, handTile2.id, handTile3.id))
        val result = fixtures.useCase(gameId, responderId, kanAction)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(newState.pendingReaction)
        assertEquals(1, newState.currentPlayerIndex, "Turn should move to the player who claimed the meld.")
        assertEquals(0, newState.tileWall.remainingCount, "The replacement tile should be drawn from the dead wall.")

        val winner = newState.players.first { it.id == responderId }
        val meld = winner.hand.melds.single()
        assertEquals(MeldType.OPEN_KAN, meld.type)
        assertEquals(setOf(handTile1, handTile2, handTile3, discardedTile), meld.tiles.toSet())
        assertEquals(rinshanTile, winner.hand.lastDrawn, "Should have drawn a replacement tile from the dead wall, not the front of the wall.")
        assertEquals(
            listOf(kanAction, GameAction.Draw),
            winner.actionHistory.takeLast(2),
            "Kan must be recorded before Draw for rinshan kaihou detection to work.",
        )

        val newDiscarder = newState.players.first { it.id == discarderId }
        assertTrue(newDiscarder.discardPile.entries.last().isTaken, "The claimed discard should be marked as taken.")
        assertEquals(
            rinshanTile.id,
            fixtures.presentationPublisher.getPublishedPlayerArea(gameId)?.drawnTileId,
            "The rinshan tile should be presented as a drawn tile (moved to the draw slot), same as a normal draw.",
        )
    }

    /**
     * 驗證明槓得標且成功補到嶺上牌時，事件流完整廣播 [GameAction.Kan] 再廣播 [GameAction.Draw]——
     * 過去只廣播了 Kan，狀態雖然正確同步但事件流不完整。
     */
    @Test
    fun `test open kan success broadcasts kan then draw event`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val handTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val handTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val handTile3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(handTile1, handTile2, handTile3)),
        )
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(rinshanTile)),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val kanAction = GameAction.Kan(GameAction.KanType.OPEN_KAN, discardedTile.id, listOf(handTile1.id, handTile2.id, handTile3.id))
        val result = fixtures.useCase(gameId, responderId, kanAction)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        assertEquals(
            listOf(kanAction, GameAction.Draw),
            fixtures.eventPublisher.getNotifiedActions(gameId, responderId, responderId),
            "Both the Kan declaration and the rinshan draw should be broadcast, in that order.",
        )
    }

    /**
     * 驗證明槓得標時牌山恰好摸盡（極端邊界情況）：副露仍正確套用，但 `lastDrawn` 維持空
     * （已知簡化，見規劃紀錄——`resolvePendingReaction` 回傳單純 `TableState`，沒有 `Outcome`
     * 通道可以回報 `WallExhausted`）。
     */
    @Test
    fun `test open kan with exhausted wall still applies meld but leaves lastDrawn empty`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val handTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val handTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val handTile3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(handTile1, handTile2, handTile3)),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val kanAction = GameAction.Kan(GameAction.KanType.OPEN_KAN, discardedTile.id, listOf(handTile1.id, handTile2.id, handTile3.id))
        val result = fixtures.useCase(gameId, responderId, kanAction)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val winner = newState.players.first { it.id == responderId }
        assertEquals(MeldType.OPEN_KAN, winner.hand.melds.single().type, "The meld itself should still be applied.")
        assertNull(winner.hand.lastDrawn, "No replacement tile is available; lastDrawn should remain empty.")
        assertEquals(0, newState.tileWall.remainingCount)
        assertEquals(
            listOf(kanAction),
            fixtures.eventPublisher.getNotifiedActions(gameId, responderId, responderId),
            "No rinshan tile was drawn, so no Draw event should be broadcast.",
        )
        assertNull(
            fixtures.presentationPublisher.getPublishedPlayerArea(gameId)?.drawnTileId,
            "No rinshan tile was drawn, so nothing should be presented as drawn either.",
        )
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
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        // 上家：可以吃（4萬、6萬）
        val chiTile4 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4))
        val chiTile6 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6))
        val chiPlayerId = Uuid.random()
        val chiPlayer = FakeMahjongPlayerFactory.create(
            id = chiPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(chiTile4, chiTile6)),
        )
        // 另一位玩家：可以碰（兩張 5萬）
        val ponTile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val ponTile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val ponPlayerId = Uuid.random()
        val ponPlayer = FakeMahjongPlayerFactory.create(
            id = ponPlayerId,
            initialSeat = Wind.WEST,
            hand = Hand(tiles = listOf(ponTile1, ponTile2)),
        )
        val bystander = FakeMahjongPlayerFactory.create(id = Uuid.random(), initialSeat = Wind.NORTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, chiPlayer, ponPlayer, bystander),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(chiPlayerId, ponPlayerId)),
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
            "Turn should go to the player who claimed the meld.",
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
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val handTiles = listOf(
            Tile.Honor.Red,
            Tile.Honor.Red,
            Tile.Honor.Red,
            Tile.Honor.Green,
            Tile.Honor.Green,
            Tile.Honor.Green,
            Tile.Honor.White,
            Tile.Honor.White,
        ).map { FakeIdentifiedTileFactory.create(it) }
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = handTiles),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Pon(discardedTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val winner = newState.players.first { it.id == responderId }
        val riichiState = winner.playerRuleState as RiichiPlayerState
        assertEquals(
            PaoLiability(PaoYaku.Daisangen, newState.relativeDirectionOf(responderId, discarderId)),
            riichiState.paoLiability,
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
                isIppatsu = true,
            ),
        )
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(FakeIdentifiedTileFactory.create(Tile.Honor.White), FakeIdentifiedTileFactory.create(Tile.Honor.White))),
        )
        val bystanderId = Uuid.random()
        val bystander = FakeMahjongPlayerFactory.create(
            id = bystanderId,
            initialSeat = Wind.WEST,
            playerRuleState = RiichiPlayerState(
                riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.South),
                isIppatsu = true,
            ),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder, bystander),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
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
     * 手牌：中中、發發發、白白白、123m、55p（大三元役滿，13 張立牌），供榮和結算測試共用。
     */
    private fun daisangenTiles() = listOf(
        Tile.Honor.Red, Tile.Honor.Red,
        Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
        Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 5),
    )

    /**
     * 驗證單獨榮和時：贏家獲得點數、放銃者失去對應點數、贏家的 actionHistory 記錄 Ron，
     * 反應視窗清除，且 currentPlayerIndex 維持不變（手牌到此結束，回合不像碰/吃/槓那樣交給贏家）。
     */
    @Test
    fun `test lone ron resolves with score deltas actionHistory and index unchanged`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val winner = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和/人和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, winner),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Ron(discardedTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(newState.pendingReaction)
        assertEquals(0, newState.currentPlayerIndex, "Ron ends the hand; currentPlayerIndex should stay untouched.")

        val newWinner = newState.players.first { it.id == responderId }
        assertEquals(32000, newWinner.score, "Non-dealer Ron on daisangen should be worth 32000 total.")
        assertEquals(GameAction.Ron(discardedTile.id), newWinner.actionHistory.last())

        val newDiscarder = newState.players.first { it.id == discarderId }
        assertEquals(-32000, newDiscarder.score, "The sole discarder should pay the full amount.")
    }

    /**
     * 驗證同時有玩家可以榮和、碰、吃時，榮和優先於碰/吃：碰與吃皆不套用，贏家僅記錄榮和結算，
     * 回合不會交給任何人。
     */
    @Test
    fun `test ron beats simultaneously submitted pon and chi`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        // 上家：可以吃（4萬、6萬）
        val chiTile4 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4))
        val chiTile6 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 6))
        val chiPlayerId = Uuid.random()
        val chiPlayer = FakeMahjongPlayerFactory.create(
            id = chiPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(chiTile4, chiTile6)),
        )
        // 另一位玩家：可以碰（兩張 5萬）
        val ponTile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val ponTile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val ponPlayerId = Uuid.random()
        val ponPlayer = FakeMahjongPlayerFactory.create(
            id = ponPlayerId,
            initialSeat = Wind.WEST,
            hand = Hand(tiles = listOf(ponTile1, ponTile2)),
        )
        // 第三位玩家：役牌白＋役牌發已成立，單騎聽 5萬，可榮和
        val ronPlayerId = Uuid.random()
        val ronPlayer = FakeMahjongPlayerFactory.create(
            id = ronPlayerId,
            initialSeat = Wind.NORTH,
            hand = Hand(
                tiles = listOf(
                    Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
                    Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
                    Tile.Numeric(Tile.Suit.Dot, 2), Tile.Numeric(Tile.Suit.Dot, 3), Tile.Numeric(Tile.Suit.Dot, 4),
                    Tile.Numeric(Tile.Suit.Bamboo, 6), Tile.Numeric(Tile.Suit.Bamboo, 7), Tile.Numeric(Tile.Suit.Bamboo, 8),
                    Tile.Numeric(Tile.Suit.Character, 5),
                ).map { FakeIdentifiedTileFactory.create(it) },
            ),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸人和
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.North)),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, chiPlayer, ponPlayer, ronPlayer),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(chiPlayerId, ponPlayerId, ronPlayerId)),
        )
        fixtures.gameRepo.setTableState(table)

        // 依序送出吃、碰：資格尚未齊全，不應結算
        val firstResult = fixtures.useCase(gameId, chiPlayerId, GameAction.Chi(discardedTile.id, listOf(chiTile4.id, chiTile6.id)))
        assertTrue(firstResult is Outcome.Success, "Expected Success but got $firstResult")
        val secondResult = fixtures.useCase(gameId, ponPlayerId, GameAction.Pon(discardedTile.id))
        assertTrue(secondResult is Outcome.Success, "Expected Success but got $secondResult")
        val stateBeforeRon = fixtures.gameRepo.getTableState(gameId)!!
        assertNotNull(stateBeforeRon.pendingReaction, "Should still wait for the Ron-eligible player to respond.")

        // 榮和玩家最後回應：資格齊全，開始結算，榮和優先於碰/吃
        val thirdResult = fixtures.useCase(gameId, ronPlayerId, GameAction.Ron(discardedTile.id))
        assertTrue(thirdResult is Outcome.Success, "Expected Success but got $thirdResult")

        val finalState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(finalState.pendingReaction)
        assertEquals(0, finalState.currentPlayerIndex, "Ron ends the hand; currentPlayerIndex should stay untouched.")

        val finalChiPlayer = finalState.players.first { it.id == chiPlayerId }
        assertTrue(finalChiPlayer.hand.melds.isEmpty(), "The Chi response should not be applied since Ron takes priority.")
        assertEquals(listOf(chiTile4, chiTile6), finalChiPlayer.hand.tiles)

        val finalPonPlayer = finalState.players.first { it.id == ponPlayerId }
        assertTrue(finalPonPlayer.hand.melds.isEmpty(), "The Pon response should not be applied since Ron takes priority.")
        assertEquals(listOf(ponTile1, ponTile2), finalPonPlayer.hand.tiles)

        val finalRonPlayer = finalState.players.first { it.id == ronPlayerId }
        assertEquals(GameAction.Ron(discardedTile.id), finalRonPlayer.actionHistory.last())
    }

    /**
     * 驗證包牌成立且包牌責任者非放銃者本人時，放銃者與包牌責任者平分點數，其餘玩家分數不受影響。
     */
    @Test
    fun `test pao-ron splits payment between discarder and pao-liable player`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val paoPlayer = FakeMahjongPlayerFactory.create(id = Uuid.random(), initialSeat = Wind.EAST)
        val winner = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和/人和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(paoLiability = PaoLiability(PaoYaku.Daisangen, RelativeDirection.Left)),
        )
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.WEST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val other = FakeMahjongPlayerFactory.create(id = Uuid.random(), initialSeat = Wind.NORTH)
        // paoPlayer 排在 winner 前一位（座位順序），故對 winner 而言方位為 Left，與上方宣告的包牌責任相符
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(paoPlayer, winner, discarder, other),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Ron(discardedTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(32000, newState.players.first { it.id == responderId }.score)
        assertEquals(-16000, newState.players.first { it.id == discarderId }.score)
        assertEquals(-16000, newState.players.first { it.id == paoPlayer.id }.score)
        assertEquals(0, newState.players.first { it.id == other.id }.score, "Uninvolved players should be untouched.")
    }

    /**
     * 驗證雙人同時榮和（多家和）時：兩位贏家各自獨立結算分數與 actionHistory，放銃者的分數扣除
     * 兩者總和，且反應視窗要等到兩人都回應完畢才會結算。
     */
    @Test
    fun `test double ron settles both winners independently`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.WEST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val dealerWinnerId = Uuid.random()
        val dealerWinner = FakeMahjongPlayerFactory.create(
            id = dealerWinnerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和/人和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val nonDealerWinner = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, dealerWinner, nonDealerWinner),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(dealerWinnerId, responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val firstResult = fixtures.useCase(gameId, dealerWinnerId, GameAction.Ron(discardedTile.id))
        assertTrue(firstResult is Outcome.Success, "Expected Success but got $firstResult")
        val stateAfterFirstRon = fixtures.gameRepo.getTableState(gameId)!!
        assertNotNull(stateAfterFirstRon.pendingReaction, "Should still wait for the other winner to respond.")

        val secondResult = fixtures.useCase(gameId, responderId, GameAction.Ron(discardedTile.id))
        assertTrue(secondResult is Outcome.Success, "Expected Success but got $secondResult")

        val finalState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(finalState.pendingReaction)
        assertEquals(0, finalState.currentPlayerIndex, "Ron ends the hand; currentPlayerIndex should stay untouched.")

        val finalDealerWinner = finalState.players.first { it.id == dealerWinnerId }
        assertEquals(48000, finalDealerWinner.score, "Dealer Ron on daisangen should be worth 48000 total.")
        assertEquals(GameAction.Ron(discardedTile.id), finalDealerWinner.actionHistory.last())

        val finalNonDealerWinner = finalState.players.first { it.id == responderId }
        assertEquals(32000, finalNonDealerWinner.score, "Non-dealer Ron on daisangen should be worth 32000 total.")
        assertEquals(GameAction.Ron(discardedTile.id), finalNonDealerWinner.actionHistory.last())

        val finalDiscarder = finalState.players.first { it.id == discarderId }
        assertEquals(-80000, finalDiscarder.score, "The discarder should pay the sum of both winners' totals.")
    }

    /**
     * 驗證三人同時榮和時，三位贏家皆各自獨立結算，放銃者的分數扣除三者總和。
     */
    @Test
    fun `test triple ron settles all three winners independently`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.NORTH,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val dealerWinnerId = Uuid.random()
        val dealerWinner = FakeMahjongPlayerFactory.create(
            id = dealerWinnerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val nonDealerWinner1 = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val nonDealerWinner2Id = Uuid.random()
        val nonDealerWinner2 = FakeMahjongPlayerFactory.create(
            id = nonDealerWinner2Id,
            initialSeat = Wind.WEST,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, dealerWinner, nonDealerWinner1, nonDealerWinner2),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(
                discarderId,
                discardedTile.id,
                setOf(dealerWinnerId, responderId, nonDealerWinner2Id),
            ),
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.useCase(gameId, dealerWinnerId, GameAction.Ron(discardedTile.id))
        fixtures.useCase(gameId, responderId, GameAction.Ron(discardedTile.id))
        val stateBeforeLastRon = fixtures.gameRepo.getTableState(gameId)!!
        assertNotNull(stateBeforeLastRon.pendingReaction, "Should still wait for the third winner to respond.")

        val lastResult = fixtures.useCase(gameId, nonDealerWinner2Id, GameAction.Ron(discardedTile.id))
        assertTrue(lastResult is Outcome.Success, "Expected Success but got $lastResult")

        val finalState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(finalState.pendingReaction)

        assertEquals(48000, finalState.players.first { it.id == dealerWinnerId }.score)
        assertEquals(32000, finalState.players.first { it.id == responderId }.score)
        assertEquals(32000, finalState.players.first { it.id == nonDealerWinner2Id }.score)
        assertEquals(
            -112000,
            finalState.players.first { it.id == discarderId }.score,
            "The discarder should pay the sum of all three winners' totals.",
        )
    }

    /**
     * 驗證單一贏家榮和時正確收下場上所有立直棒。
     */
    @Test
    fun `test lone ron collects stick pot`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val winner = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, winner),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 3),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Ron(discardedTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(
            32000 + 3000,
            newState.players.first { it.id == responderId }.score,
            "Winner's score should include both the Ron total and the 3 sticks (1000 each).",
        )
        assertEquals(0, (newState.dynamicRuleState as RiichiDynamicState).riichiStickCount)
    }

    /**
     * 驗證多家和時供託由頭跳順位最近的贏家收下，不是所有贏家均分。
     */
    @Test
    fun `test multi-ron stick pot goes to the nearest winner only`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.WEST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val dealerWinnerId = Uuid.random()
        val dealerWinner = FakeMahjongPlayerFactory.create(
            id = dealerWinnerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val nonDealerWinner = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        // players 順序：discarder → dealerWinner → nonDealerWinner，故 dealerWinner 是離放銃者
        // 最近的下家（頭跳順位最前）
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, dealerWinner, nonDealerWinner),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 2),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(dealerWinnerId, responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.useCase(gameId, dealerWinnerId, GameAction.Ron(discardedTile.id))
        val result = fixtures.useCase(gameId, responderId, GameAction.Ron(discardedTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val finalState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(
            48000 + 2000,
            finalState.players.first { it.id == dealerWinnerId }.score,
            "The nearest winner (immediately after the discarder) should collect the sticks.",
        )
        assertEquals(
            32000,
            finalState.players.first { it.id == responderId }.score,
            "The farther winner should not receive any of the stick pot.",
        )
        assertEquals(0, (finalState.dynamicRuleState as RiichiDynamicState).riichiStickCount)
    }

    /**
     * 驗證放過原本可以榮和的牌時，也會記錄同巡振聽（不只放過碰牌需要記錄）。
     */
    @Test
    fun `test pass on lone ron-eligible player records passed tile for furiten`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        // 役牌白＋役牌發已成立，單騎聽中，可榮和捨出的紅中
        val responder = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(
                tiles = listOf(
                    Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
                    Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
                    Tile.Numeric(Tile.Suit.Dot, 2), Tile.Numeric(Tile.Suit.Dot, 3), Tile.Numeric(Tile.Suit.Dot, 4),
                    Tile.Numeric(Tile.Suit.Bamboo, 5), Tile.Numeric(Tile.Suit.Bamboo, 6), Tile.Numeric(Tile.Suit.Bamboo, 7),
                    Tile.Honor.Red,
                ).map { FakeIdentifiedTileFactory.create(it) },
            ),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Pass)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(newState.pendingReaction, "Everyone eligible has responded, so the reaction window should close.")
        val updatedResponder = newState.players.first { it.id == responderId }
        assertTrue(
            Tile.Honor.Red in updatedResponder.passedTilesInRound,
            "Passing on an available Ron should be recorded for same-go-around furiten (同巡振聽).",
        )
    }

    /**
     * 驗證振聽時榮和被擋下：玩家先前已經打過（或放過）等待的牌，重新驗證合法動作時 Ron 不會出現，
     * 直接回傳 [GameError.IllegalAction]（沿用既有的 getLegalActions 重新驗證機制，無需新程式碼）。
     */
    @Test
    fun `test furiten blocks ron via re-validated legal actions`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        // 役牌白＋役牌發已成立，單騎聽中；但自己先前已經打過一張中，屬於振聽
        val furitenPlayer = FakeMahjongPlayerFactory.create(
            id = responderId,
            initialSeat = Wind.SOUTH,
            hand = Hand(
                tiles = listOf(
                    Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
                    Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
                    Tile.Numeric(Tile.Suit.Dot, 2), Tile.Numeric(Tile.Suit.Dot, 3), Tile.Numeric(Tile.Suit.Dot, 4),
                    Tile.Numeric(Tile.Suit.Bamboo, 5), Tile.Numeric(Tile.Suit.Bamboo, 6), Tile.Numeric(Tile.Suit.Bamboo, 7),
                    Tile.Honor.Red,
                ).map { FakeIdentifiedTileFactory.create(it) },
            ),
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.Red)),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, furitenPlayer),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Ron(discardedTile.id))

        assertTrue(result is Outcome.Error, "Furiten should block Ron even though the hand is otherwise complete.")
        assertEquals(GameError.IllegalAction(responderId, gameId, GameAction.Ron(discardedTile.id)), result.error)
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
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val ineligiblePlayerId = Uuid.random()
        val ineligiblePlayer = FakeMahjongPlayerFactory.create(id = ineligiblePlayerId, initialSeat = Wind.SOUTH)
        val eligiblePlayer = FakeMahjongPlayerFactory.create(id = responderId, initialSeat = Wind.WEST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, ineligiblePlayer, eligiblePlayer),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
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
            discardPile = FakeDiscardPile().discardTile(discardedTile),
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
                responses = mapOf(responderId to GameAction.Pass),
            ),
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
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        // 手牌沒有任何白板，無法碰
        val responder = FakeMahjongPlayerFactory.create(id = responderId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, responder),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(responderId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, responderId, GameAction.Pon(discardedTile.id))

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.IllegalAction(responderId, gameId, GameAction.Pon(discardedTile.id)), result.error)
    }
}
