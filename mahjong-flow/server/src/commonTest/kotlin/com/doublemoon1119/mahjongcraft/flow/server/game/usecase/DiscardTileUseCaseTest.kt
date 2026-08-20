package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
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
 * [DiscardTileUseCase] 的單元測試類別。
 *
 * 驗證捨牌的業務邏輯，包含回合驗證、手牌與牌河狀態更新、推進下一位玩家或開啟反應視窗，
 * 以及快照與事件的同步行為。
 */
class DiscardTileUseCaseTest {

    private val gameId = Uuid.random()
    private val currentPlayerId = Uuid.random()
    private val otherPlayerId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val useCase =
            DiscardTileUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher)
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
            hand = Hand(tiles = listOf(handTile), lastDrawn = drawnTile),
        )
        val otherPlayer = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
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
        assertNull(newState.pendingReaction, "No one is eligible to react, so no reaction window should open.")
    }

    /**
     * 驗證捨牌後會觸發平台呈現層，重新排列立牌列並更新牌河，且沒有立直宣告牌時側身標記為 null。
     */
    @Test
    fun `test discard tile publishes hand and discard pile presentation`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(handTile), lastDrawn = drawnTile),
        )
        val otherPlayer = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertEquals(listOf(handTile.id), fixtures.presentationPublisher.getPublishedPlayerArea(gameId)?.standingTileIds)
        val publishedDiscardPile = fixtures.presentationPublisher.getPublishedDiscardPile(gameId)
        assertNotNull(publishedDiscardPile)
        assertEquals(0, publishedDiscardPile.seatIndex)
        assertEquals(listOf(drawnTile.id), publishedDiscardPile.discardTileIds)
        assertEquals(null, publishedDiscardPile.sidewaysMarkedTileId)
    }

    /**
     * 驗證已宣告立直的玩家後續再捨牌時，牌河更新呈現的側身標記仍正確指向較早的立直宣告牌
     * （[SidewaysMarkedDiscardPile.sidewaysMarkedTileId]
     * 掃描整個牌河推導，不受這次捨牌影響）。
     */
    @Test
    fun `test discard tile publishes sideways marked tile from an earlier riichi declaration`() = runTest {
        val fixtures = Fixtures()
        val riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val alreadyDiscarded = RiichiDiscardPile().discard(RiichiDiscardEntry(riichiTile, isRiichi = true))
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = drawnTile),
            discardPile = alreadyDiscarded,
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        val publishedDiscardPile = fixtures.presentationPublisher.getPublishedDiscardPile(gameId)
        assertNotNull(publishedDiscardPile)
        assertEquals(listOf(riichiTile.id, drawnTile.id), publishedDiscardPile.discardTileIds)
        assertEquals(riichiTile.id, publishedDiscardPile.sidewaysMarkedTileId)
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
            hand = Hand(tiles = listOf(handTile), lastDrawn = drawnTile),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
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
     * 驗證捨出的牌讓其他玩家有資格碰牌時，不推進回合，改為開啟反應視窗。
     */
    @Test
    fun `test discard tile opens pending reaction when someone is eligible to react`() = runTest {
        val fixtures = Fixtures()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = discardedTile),
        )
        // 另一位玩家手牌中有兩張東風，湊滿碰牌的條件
        val otherPlayer = FakeMahjongPlayerFactory.create(
            id = otherPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(
                tiles = listOf(
                    FakeIdentifiedTileFactory.create(Tile.Honor.East),
                    FakeIdentifiedTileFactory.create(Tile.Honor.East),
                ),
            ),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, discardedTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        assertEquals(0, newState.currentPlayerIndex, "Turn should not advance while a reaction is pending.")
        val pendingReaction = newState.pendingReaction
        assertNotNull(pendingReaction, "A reaction window should open since otherPlayer can Pon.")
        assertEquals(currentPlayerId, pendingReaction.discarderId)
        assertEquals(discardedTile.id, pendingReaction.tileId)
        assertEquals(setOf(otherPlayerId), pendingReaction.eligiblePlayerIds)
    }

    /**
     * 手牌：白白白、發發發、234p、567s，加上單張中（役牌白＋役牌發已成立，單騎聽中）,
     * 供榮和資格測試共用。刻意採用單騎聽牌（聽的牌手牌中只有 1 張），而非碰/槓聽牌那種
     * 手牌中已有 2 張的形狀，避免榮和資格意外與碰牌資格重疊，干擾測試判斷。
     */
    private fun ronTankiWaitTiles() = listOf(
        Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
        Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
        Tile.Numeric(Tile.Suit.Dot, 2),
        Tile.Numeric(Tile.Suit.Dot, 3),
        Tile.Numeric(Tile.Suit.Dot, 4),
        Tile.Numeric(Tile.Suit.Bamboo, 5),
        Tile.Numeric(Tile.Suit.Bamboo, 6),
        Tile.Numeric(Tile.Suit.Bamboo, 7),
        Tile.Honor.Red,
    )

    /**
     * 驗證捨出的牌讓唯一一位其他玩家有資格榮和時，正確開啟反應視窗並給予榮和資格。
     */
    @Test
    fun `test discard tile opens ron eligibility when exactly one player can ron`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = winningTile),
        )
        // 役牌白＋役牌發已成立，單騎聽中，捨出的紅中即可榮和
        val otherPlayer = FakeMahjongPlayerFactory.create(
            id = otherPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        assertEquals(0, newState.currentPlayerIndex, "Turn should not advance while a reaction is pending.")
        val pendingReaction = newState.pendingReaction
        assertNotNull(pendingReaction, "A reaction window should open since otherPlayer can Ron.")
        assertEquals(setOf(otherPlayerId), pendingReaction.eligiblePlayerIds)
    }

    /**
     * 驗證雙響時規則設定為流局（[RonResolution.ABORTIVE_DRAW]）時，這張捨牌會讓本局直接結束
     * （途中流局）：不開反應視窗（連原本可以碰的玩家也一併作廢）、全員 `actionHistory` 皆記錄
     * `ExhaustiveDraw(SanchaHou)`、`currentPlayerIndex` 不變、不結算任何點數。
     */
    @Test
    fun `test discard tile triggers abortive draw when double ron resolves to ABORTIVE_DRAW`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = winningTile),
        ).copy(score = 25000)
        val ronEligiblePlayerId1 = otherPlayerId
        val ronEligiblePlayerId2 = Uuid.random()
        val ponEligiblePlayerId = Uuid.random()
        // 兩位玩家皆單騎聽中，同時可榮和捨出的紅中
        val ronPlayer1 = FakeMahjongPlayerFactory.create(
            id = ronEligiblePlayerId1,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        ).copy(score = 25000)
        val ronPlayer2 = FakeMahjongPlayerFactory.create(
            id = ronEligiblePlayerId2,
            initialSeat = Wind.WEST,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        ).copy(score = 25000)
        // 手牌中有兩張紅中，湊滿碰牌的條件，驗證流局判斷會讓這個碰牌資格一併作廢
        val ponPlayer = FakeMahjongPlayerFactory.create(
            id = ponEligiblePlayerId,
            initialSeat = Wind.NORTH,
            hand = Hand(
                tiles = listOf(
                    FakeIdentifiedTileFactory.create(Tile.Honor.Red),
                    FakeIdentifiedTileFactory.create(Tile.Honor.Red),
                ),
            ),
        ).copy(score = 25000)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, ronPlayer1, ronPlayer2, ponPlayer),
            config = RiichiRuleConfig(
                multiRonPolicy = MultiRonPolicy(
                    doubleRonResolution = RonResolution.ABORTIVE_DRAW,
                    tripleRonResolution = RonResolution.ABORTIVE_DRAW,
                ),
            ),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        assertNull(
            newState.pendingReaction,
            "The hand already ended via the abortive draw; no reaction window should open, not even for ponPlayer.",
        )
        assertEquals(0, newState.currentPlayerIndex, "Turn advancement is left to AdvanceRoundUseCase.")
        val expectedAction = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SanchaHou)
        newState.players.forEach { player ->
            assertEquals(
                expectedAction,
                player.actionHistory.last(),
                "Every player should have ExhaustiveDraw recorded.",
            )
            assertEquals(25000, player.score, "An abortive draw should not exchange any points.")
        }
    }

    /**
     * 驗證三響（三家和了本義）時規則設定為流局時，同樣正確觸發途中流局。
     */
    @Test
    fun `test discard tile triggers abortive draw when triple ron resolves to ABORTIVE_DRAW`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = winningTile),
        )
        val ronPlayer1 = FakeMahjongPlayerFactory.create(
            id = otherPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val ronPlayer2 = FakeMahjongPlayerFactory.create(
            id = Uuid.random(),
            initialSeat = Wind.WEST,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val ronPlayer3 = FakeMahjongPlayerFactory.create(
            id = Uuid.random(),
            initialSeat = Wind.NORTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, ronPlayer1, ronPlayer2, ronPlayer3),
            config = RiichiRuleConfig(
                multiRonPolicy = MultiRonPolicy(
                    doubleRonResolution = RonResolution.ALL_WINNERS,
                    tripleRonResolution = RonResolution.ABORTIVE_DRAW,
                ),
            ),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        assertNull(newState.pendingReaction)
        val expectedAction = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SanchaHou)
        newState.players.forEach { player -> assertEquals(expectedAction, player.actionHistory.last()) }
    }

    /**
     * 驗證流局觸發時，所有玩家皆先收到捨牌事件、再收到流局事件（依序廣播）。
     */
    @Test
    fun `test discard tile broadcasts Discard then ExhaustiveDraw when abortive draw is triggered`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = winningTile),
        )
        val ronPlayer1 = FakeMahjongPlayerFactory.create(
            id = otherPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val ronPlayer2 = FakeMahjongPlayerFactory.create(
            id = Uuid.random(),
            initialSeat = Wind.WEST,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, ronPlayer1, ronPlayer2),
            config = RiichiRuleConfig(
                multiRonPolicy = MultiRonPolicy(
                    doubleRonResolution = RonResolution.ABORTIVE_DRAW,
                    tripleRonResolution = RonResolution.ABORTIVE_DRAW,
                ),
            ),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        val notifiedActions = fixtures.eventPublisher.getNotifiedActions(gameId, currentPlayerId, currentPlayerId)
        assertEquals(
            listOf(GameAction.Discard(winningTile.id), GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SanchaHou)),
            notifiedActions,
        )
    }

    /**
     * 驗證雙響時規則設定為多家和（[RonResolution.ALL_WINNERS]，Riichi 預設）時，
     * 兩位符合資格的玩家皆取得榮和資格。
     */
    @Test
    fun `test discard tile grants ron eligibility to both players when double ron resolves to all winners`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = winningTile),
        )
        val ronPlayer1Id = otherPlayerId
        val ronPlayer2Id = Uuid.random()
        val ronPlayer1 = FakeMahjongPlayerFactory.create(
            id = ronPlayer1Id,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val ronPlayer2 = FakeMahjongPlayerFactory.create(
            id = ronPlayer2Id,
            initialSeat = Wind.WEST,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, ronPlayer1, ronPlayer2),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        val pendingReaction = newState.pendingReaction
        assertNotNull(pendingReaction)
        assertEquals(setOf(ronPlayer1Id, ronPlayer2Id), pendingReaction.eligiblePlayerIds)
    }

    /**
     * 驗證雙響時規則設定為頭跳（[RonResolution.NEAREST_WINNER]，Taiwan 預設）時，
     * 只有順位最接近放銃者下家的那一位玩家取得榮和資格。
     */
    @Test
    fun `test discard tile grants ron eligibility to nearest player only when double ron resolves to nearest winner`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = winningTile),
        )
        val nearestPlayerId = otherPlayerId
        val fartherPlayerId = Uuid.random()
        // nearestPlayer 坐在放銃者（currentPlayer）的下家，順位比 fartherPlayer 更靠前
        val nearestPlayer = FakeMahjongPlayerFactory.create(
            id = nearestPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val fartherPlayer = FakeMahjongPlayerFactory.create(
            id = fartherPlayerId,
            initialSeat = Wind.WEST,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, nearestPlayer, fartherPlayer),
            config = RiichiRuleConfig(
                multiRonPolicy = MultiRonPolicy(
                    doubleRonResolution = RonResolution.NEAREST_WINNER,
                    tripleRonResolution = RonResolution.NEAREST_WINNER,
                ),
            ),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        val pendingReaction = newState.pendingReaction
        assertNotNull(pendingReaction)
        assertEquals(setOf(nearestPlayerId), pendingReaction.eligiblePlayerIds)
    }

    /**
     * 驗證三響時規則設定為多家和時，三位符合資格的玩家皆取得榮和資格。
     */
    @Test
    fun `test discard tile grants ron eligibility to all three players when triple ron resolves to all winners`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = winningTile),
        )
        val southId = otherPlayerId
        val westId = Uuid.random()
        val northId = Uuid.random()
        val south = FakeMahjongPlayerFactory.create(
            id = southId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val west = FakeMahjongPlayerFactory.create(
            id = westId,
            initialSeat = Wind.WEST,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val north = FakeMahjongPlayerFactory.create(
            id = northId,
            initialSeat = Wind.NORTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, south, west, north),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        val pendingReaction = newState.pendingReaction
        assertNotNull(pendingReaction)
        assertEquals(setOf(southId, westId, northId), pendingReaction.eligiblePlayerIds)
    }

    /**
     * 驗證三響時規則設定為頭跳時，只有順位最接近放銃者下家的那一位玩家取得榮和資格。
     */
    @Test
    fun `test discard tile grants ron eligibility to nearest player only when triple ron resolves to nearest winner`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = winningTile),
        )
        val southId = otherPlayerId
        val westId = Uuid.random()
        val northId = Uuid.random()
        val south = FakeMahjongPlayerFactory.create(
            id = southId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val west = FakeMahjongPlayerFactory.create(
            id = westId,
            initialSeat = Wind.WEST,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val north = FakeMahjongPlayerFactory.create(
            id = northId,
            initialSeat = Wind.NORTH,
            hand = Hand(tiles = ronTankiWaitTiles().map { FakeIdentifiedTileFactory.create(it) }),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, south, west, north),
            config = RiichiRuleConfig(
                multiRonPolicy = MultiRonPolicy(
                    doubleRonResolution = RonResolution.NEAREST_WINNER,
                    tripleRonResolution = RonResolution.NEAREST_WINNER,
                ),
            ),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")

        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        val pendingReaction = newState.pendingReaction
        assertNotNull(pendingReaction)
        assertEquals(
            setOf(southId),
            pendingReaction.eligiblePlayerIds,
            "South sits immediately after the discarder, so South should win under NEAREST_WINNER.",
        )
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
            hand = Hand(lastDrawn = drawnTile),
        )
        val otherPlayer = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)
        fixtures.snapshotRepo.setSnapshot(currentPlayerId, table.toSnapshot(setOf(currentPlayerId)))
        fixtures.snapshotRepo.setSnapshot(otherPlayerId, table.toSnapshot(setOf(otherPlayerId)))

        fixtures.useCase(gameId, currentPlayerId, drawnTile.id)

        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, currentPlayerId))
        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, otherPlayerId))
        assertEquals(
            GameAction.Discard(drawnTile.id),
            fixtures.eventPublisher.getNotifiedAction(gameId, currentPlayerId, currentPlayerId),
        )
        assertEquals(
            GameAction.Discard(drawnTile.id),
            fixtures.eventPublisher.getNotifiedAction(gameId, otherPlayerId, currentPlayerId),
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
            hand = Hand(lastDrawn = drawnTile),
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
            hand = Hand(lastDrawn = drawnTile),
        )
        val otherPlayer = FakeMahjongPlayerFactory.create(
            id = otherPlayerId,
            initialSeat = Wind.SOUTH,
            hand = Hand(lastDrawn = handTile),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, otherPlayer),
            currentPlayerIndex = 0,
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
            hand = Hand(tiles = listOf(handTile)),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(currentPlayer), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, handTile.id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(currentPlayerId, gameId, GameAction.Discard(handTile.id)),
            result.error,
        )
    }

    /**
     * 驗證玩家剛吃／碰成立、`lastDrawn` 仍為 null 時仍然可以捨牌。
     *
     * 迴歸測試：`lastDrawn == null` 的驗證原本沒有排除「剛吃／碰，該直接捨牌」這種情境，導致這種
     * 捨牌被誤判成「還沒摸牌」而拒絕——`AiTurnDriver`／`ForcedAutoPlayDriver` 都會在吃／碰成立後
     * 送出捨牌指令，若被這裡拒絕，玩家的回合永遠無法推進，且不會拋出例外，只會靜默失敗。
     */
    @Test
    fun `test discard tile succeeds right after claiming a meld with no lastDrawn`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(handTile)),
        ).recordAction(GameAction.Pon(tileId = Uuid.random()))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, handTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
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
            hand = Hand(lastDrawn = drawnTile),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(currentPlayer), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)
        val unknownTileId = Uuid.random()

        val result = fixtures.useCase(gameId, currentPlayerId, unknownTileId)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(currentPlayerId, gameId, GameAction.Discard(unknownTileId)),
            result.error,
        )
    }

    /**
     * 驗證第一巡全員的第一張捨牌皆為同一種風牌時，觸發四風連打：不開反應視窗、全員記錄
     * `ExhaustiveDraw(SuufonRenda)`、不結算任何點數。
     */
    @Test
    fun `test discard tile triggers SuufonRenda when all first discards are the same wind`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val alreadyDiscardedEast = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))
        val east = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, discardPile = alreadyDiscardedEast)
            .copy(score = 25000)
        val south = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH, discardPile = alreadyDiscardedEast)
            .copy(score = 25000)
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST, discardPile = alreadyDiscardedEast)
            .copy(score = 25000)
        val north = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.NORTH,
            hand = Hand(lastDrawn = winningTile),
        ).copy(score = 25000)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(east, south, west, north),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 3,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        assertNull(newState.pendingReaction)
        val expectedAction = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SuufonRenda)
        newState.players.forEach { player ->
            assertEquals(expectedAction, player.actionHistory.last())
            assertEquals(25000, player.score, "Suufon renda is an abortive draw and should not exchange any points.")
        }
    }

    /**
     * 驗證第一張捨牌種類不同時，不觸發四風連打，正常推進到下一位玩家。
     */
    @Test
    fun `test discard tile does not trigger SuufonRenda when first discards differ`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val alreadyDiscardedEast = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))
        val alreadyDiscardedSouth = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South))
        val east = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, discardPile = alreadyDiscardedEast)
        val south = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH, discardPile = alreadyDiscardedSouth)
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST, discardPile = alreadyDiscardedEast)
        val north = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.NORTH,
            hand = Hand(lastDrawn = winningTile),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(east, south, west, north),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 3,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        assertNull(newState.pendingReaction)
        assertEquals(0, newState.currentPlayerIndex, "Turn should advance normally to the next player.")
        newState.players.forEach { player -> assertTrue(player.actionHistory.none { it is GameAction.ExhaustiveDraw }) }
    }

    /**
     * 驗證即使四家第一張捨牌皆同風牌，只要有玩家可以碰這張牌，就不會觸發四風連打——改為正常開啟
     * 反應視窗，因為「這張牌可能被鳴走」已經不是「無人反應」的情境。
     */
    @Test
    fun `test discard tile does not trigger SuufonRenda when someone can pon`() = runTest {
        val fixtures = Fixtures()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val alreadyDiscardedEast = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))
        val east = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, discardPile = alreadyDiscardedEast)
        // 手牌中有兩張東風，湊滿碰牌的條件
        val south = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            discardPile = alreadyDiscardedEast,
            hand = Hand(
                tiles = listOf(
                    FakeIdentifiedTileFactory.create(Tile.Honor.East),
                    FakeIdentifiedTileFactory.create(Tile.Honor.East),
                ),
            ),
        )
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST, discardPile = alreadyDiscardedEast)
        val north = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.NORTH,
            hand = Hand(lastDrawn = winningTile),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(east, south, west, north),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 3,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, currentPlayerId, winningTile.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)
        assertNotNull(newState)
        assertNotNull(newState.pendingReaction, "A reaction window should open since south can pon.")
        newState.players.forEach { player -> assertTrue(player.actionHistory.none { it is GameAction.ExhaustiveDraw }) }
    }
}
