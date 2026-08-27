package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingKanReaction
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
 * [DeclareKanUseCase] 的單元測試類別。
 *
 * 驗證暗槓/加槓宣告的合法性驗證（交給 `LegalActionValidator`）、套用副露後正確依序記錄
 * `Kan` → `Draw`、從死牌區補摸嶺上牌、牌山摸盡時的 all-or-nothing 行為，以及快照與事件的同步行為。
 */
class DeclareKanUseCaseTest {

    private val gameId = Uuid.random()
    private val playerId = Uuid.random()

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val useCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher)
    }

    private val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))

    /**
     * 驗證暗槓成功宣告：手牌 3 張 + 摸到第 4 張同種牌 → 副露正確加入 `exposedMelds`、
     * 手牌對應 4 張牌被移除、`actionHistory` 依序記錄 `Kan` → `Draw`、`lastDrawn` 為補摸的嶺上牌、
     * 牌山正確縮減 1 張。
     */
    @Test
    fun `test closed kan success applies meld and draws replacement tile`() = runTest {
        val fixtures = Fixtures()
        val east1 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east2 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east3 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east4 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(east1, east2, east3), lastDrawn = east4),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            initialDeadWall = listOf(rinshanTile),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, east4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val updated = newState.players.first { it.id == playerId }
        assertTrue(updated.hand.tiles.isEmpty(), "All 4 tiles forming the kan should be removed from standing tiles.")
        val meld = updated.hand.melds.single()
        assertEquals(MeldType.CLOSED_KAN, meld.type)
        assertEquals(setOf(east1, east2, east3, east4), meld.tiles.toSet())
        assertEquals(rinshanTile, updated.hand.lastDrawn, "Should have drawn a replacement tile from the dead wall.")
        assertEquals(
            listOf(GameAction.Kan(GameAction.KanType.CLOSED_KAN, east4.id, listOf(east1.id, east2.id, east3.id)), GameAction.Draw),
            updated.actionHistory.takeLast(2),
            "Kan must be recorded before Draw for rinshan kaihou detection to work.",
        )
        assertEquals(table.tileWall, newState.tileWall, "The rinshan tile comes from the dead wall reserve, not the live wall.")
        assertEquals(
            rinshanTile.id,
            fixtures.presentationPublisher.getPublishedPlayerArea(gameId)?.drawnTileId,
            "The rinshan tile should be presented as a drawn tile (moved to the draw slot), same as a normal draw.",
        )
    }

    /**
     * 驗證槓牌成立後會透過 `TileWallRevealable` 通知平台呈現層目前完整該公開翻面的王牌集合（例如
     * 槓寶牌）——不是只有「有沒有呼叫」，而是真的用了 `RiichiDynamicState.getVisibleTileIds` 算出來的
     * 值，不支援 `TileWallRevealable` 的桌況（`dynamicRuleState` 為 null）則完全不會呼叫。
     */
    @Test
    fun `test closed kan success publishes newly revealed dead wall tiles`() = runTest {
        val fixtures = Fixtures()
        val east1 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east2 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east3 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east4 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(east1, east2, east3), lastDrawn = east4),
        )
        val deadWall = List(14) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)) }
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            initialDeadWall = deadWall,
            currentPlayerIndex = 0,
            dynamicRuleState = RiichiDynamicState(),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, east4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val currentState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(
            RiichiDynamicState().getVisibleTileIds(currentState) - RiichiDynamicState().getVisibleTileIds(table),
            assertNotNull(
                fixtures.presentationPublisher.getPublishedDeadWallReveal(gameId),
                "A kan-dora reveal should have been published for a rule that implements TileWallRevealable.",
            ),
            "Only the newly exposed indicator should be published for animation.",
        )
    }

    /** 驗證第二次槓只發布第二次新增的指示牌，不讓第一張已公開指示牌重播動畫。 */
    @Test
    fun `test second kan does not republish the first revealed indicator`() = runTest {
        val fixtures = Fixtures()
        val previousKanTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.South) }
        val eastTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(
                tiles = eastTiles.take(3),
                melds = listOf(
                    Meld(
                        MeldType.CLOSED_KAN,
                        previousKanTiles,
                        sourceDirection = RelativeDirection.Self,
                    ),
                ),
                lastDrawn = eastTiles.last(),
            ),
        )
        val deadWall = List(14) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)) }
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            initialDeadWall = deadWall,
            currentPlayerIndex = 0,
            dynamicRuleState = RiichiDynamicState(),
        )
        val previouslyVisible = RiichiDynamicState().getVisibleTileIds(table)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, eastTiles.last().id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val currentState = fixtures.gameRepo.getTableState(gameId)!!
        val published = assertNotNull(fixtures.presentationPublisher.getPublishedDeadWallReveal(gameId))
        assertEquals(RiichiDynamicState().getVisibleTileIds(currentState) - previouslyVisible, published)
        assertTrue(published.intersect(previouslyVisible).isEmpty(), "Previously revealed indicators must not be republished")
    }

    /**
     * 驗證加槓成功宣告：已有 PON 副露、摸到第 4 張同種牌 → 副露原地升級為 ADDED_KAN，
     * 保留原本的 sourceTile/sourceDirection。
     */
    @Test
    fun `test added kan success upgrades existing pon meld and preserves source`() = runTest {
        val fixtures = Fixtures()
        val south1 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val south2 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val south3 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val south4 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val existingPon = Meld(MeldType.PON, listOf(south1, south2, south3), sourceTile = south3, sourceDirection = RelativeDirection.Left)
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon), lastDrawn = south4),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            initialDeadWall = listOf(rinshanTile),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, south4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val updated = newState.players.first { it.id == playerId }
        val meld = updated.hand.melds.single()
        assertEquals(MeldType.ADDED_KAN, meld.type)
        assertEquals(setOf(south1, south2, south3, south4), meld.tiles.toSet())
        assertEquals(south3, meld.sourceTile, "Added kan should preserve the original Pon's source tile.")
        assertEquals(RelativeDirection.Left, meld.sourceDirection, "Added kan should preserve the original Pon's source direction.")
        assertEquals(rinshanTile, updated.hand.lastDrawn)
        assertEquals(
            listOf(GameAction.Kan(GameAction.KanType.ADDED_KAN, south4.id, emptyList()), GameAction.Draw),
            updated.actionHistory.takeLast(2),
        )
    }

    /**
     * 驗證立直後暗槓會改變面子結構時（`isMeldStructureChanged`：手牌中有與槓牌同花色、數字距離
     * 2 以內的牌，代表這組刻子與其他牌具有組成順子的「血緣關係」）不合法，不需要驗證聽牌本身
     * 是否改變——`checkClosedKanAfterRiichi` 會先做這個結構檢查，成立就直接擋下。
     */
    @Test
    fun `test closed kan fails after riichi when it would change meld structure`() = runTest {
        val fixtures = Fixtures()
        val fifthMan1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val fifthMan2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val fifthMan3 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val fifthMan4 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        // 4 萬與 5 萬數字距離為 1（<= 2），觸發 isMeldStructureChanged
        val adjacentTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 4))
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(fifthMan1, fifthMan2, fifthMan3, adjacentTile), lastDrawn = fifthMan4),
            playerRuleState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.North)),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(rinshanTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, fifthMan4.id)

        assertTrue(result is Outcome.Error, "Expected Error but got $result")
        assertEquals(
            GameError.IllegalAction(
                playerId,
                gameId,
                GameAction.Kan(GameAction.KanType.CLOSED_KAN, fifthMan4.id, emptyList()),
            ),
            result.error,
        )
    }

    /** 驗證四張相同牌原本都在立牌中時，摸入其他牌後仍可選擇該組暗槓。 */
    @Test
    fun `test closed kan can use four standing tiles after drawing a different tile`() = runTest {
        val fixtures = Fixtures()
        val eastTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }
        val unrelatedDraw = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9))
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = eastTiles, lastDrawn = unrelatedDraw),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            initialDeadWall = listOf(rinshanTile),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, eastTiles.first().id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val hand = fixtures.gameRepo.getTableState(gameId)!!.currentPlayer.hand
        assertEquals(MeldType.CLOSED_KAN, hand.melds.single().type)
        assertEquals(eastTiles.map { it.id }.toSet(), hand.melds.single().tiles.map { it.id }.toSet())
        assertTrue(hand.tiles.any { it.id == unrelatedDraw.id }, "The unrelated drawn tile must remain in the hand")
        assertEquals(rinshanTile.id, hand.lastDrawn?.id)
    }

    /** 驗證 `tileId` 不屬於任何合法槓牌候選時回傳 [GameError.IllegalAction]。 */
    @Test
    fun `test declare kan fails when tileId does not match lastDrawn`() = runTest {
        val fixtures = Fixtures()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val otherTileId = Uuid.random()
        val declarer = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = lastDrawn))
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(declarer), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, otherTileId)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(playerId, gameId, GameAction.Kan(GameAction.KanType.CLOSED_KAN, otherTileId, emptyList())),
            result.error,
        )
    }

    /**
     * 驗證牌山恰好在補摸嶺上牌時摸盡，回傳 [GameError.WallExhausted]，且桌況維持宣告前的樣子不變
     * （all-or-nothing：`gameRepository.update` 回傳原始 `state`，不會半套用副露）。
     */
    @Test
    fun `test declare kan fails with wall exhausted and does not partially apply the meld`() = runTest {
        val fixtures = Fixtures()
        val east1 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east2 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east3 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east4 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(east1, east2, east3), lastDrawn = east4),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            // tileWall 刻意不是空的——那是「河底/海底不可鳴牌」的判定條件，跟這裡要測的「嶺上牌保留區
            // （initialDeadWall，預設空清單）摸盡」是兩回事，tileWall 空的話這次暗槓在走到補摸嶺上牌
            // 之前就會先被 RiichiLegalActionValidator 擋下，這個測試就測不到真正想驗證的情境。
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, east4.id)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.WallExhausted(gameId), result.error)
        val unchangedState = fixtures.gameRepo.getTableState(gameId)!!
        val unchangedPlayer = unchangedState.players.first { it.id == playerId }
        assertTrue(unchangedPlayer.hand.melds.isEmpty(), "The meld should not be applied when the replacement draw fails.")
        assertEquals(east4, unchangedPlayer.hand.lastDrawn, "The player's hand should remain exactly as it was before the declaration.")
        assertEquals(0, unchangedState.initialDeadWall.size, "The rinshan reserve (initialDeadWall) is what's actually exhausted here.")
    }

    /**
     * 驗證宣告 [GameAction.KanType.OPEN_KAN]（走 [RespondToDiscardUseCase] 的種類）時提早擋下，
     * 回傳 [GameError.IllegalAction]。
     */
    @Test
    fun `test declare kan fails for open kan type`() = runTest {
        val fixtures = Fixtures()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val declarer = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = lastDrawn))
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(declarer), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.OPEN_KAN, lastDrawn.id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(playerId, gameId, GameAction.Kan(GameAction.KanType.OPEN_KAN, lastDrawn.id, emptyList())),
            result.error,
        )
    }

    /**
     * 驗證非當前回合玩家嘗試宣告時回傳 [GameError.NotPlayersTurn]。
     */
    @Test
    fun `test declare kan fails when not players turn`() = runTest {
        val fixtures = Fixtures()
        val currentPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val declarer = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(currentPlayer, declarer),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, Uuid.random())

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.NotPlayersTurn(playerId, gameId), result.error)
    }

    /**
     * 驗證發起請求的玩家不在該對局中時回傳 [GameError.PlayerNotInGame]。
     */
    @Test
    fun `test declare kan fails when player not in game`() = runTest {
        val fixtures = Fixtures()
        val declarer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(declarer), config = RiichiRuleConfig())
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, Uuid.random())

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.PlayerNotInGame(playerId, gameId), result.error)
    }

    /**
     * 驗證對局不存在時回傳 [GameError.GameNotFound]。
     */
    @Test
    fun `test declare kan fails when game not found`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, Uuid.random())

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.GameNotFound(gameId), result.error)
    }

    /**
     * 驗證宣告成功後所有觀察者的快照皆同步更新，且所有玩家皆依序收到 Kan、Draw 事件通知。
     */
    @Test
    fun `test declare kan syncs snapshot and notifies all players in order`() = runTest {
        val fixtures = Fixtures()
        val east1 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east2 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east3 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east4 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(east1, east2, east3), lastDrawn = east4),
        )
        val otherId = Uuid.random()
        val other = FakeMahjongPlayerFactory.create(id = otherId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, other),
            config = RiichiRuleConfig(),
            initialDeadWall = listOf(rinshanTile),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)
        fixtures.snapshotRepo.setSnapshot(playerId, table.toSnapshot(setOf(playerId)))
        fixtures.snapshotRepo.setSnapshot(otherId, table.toSnapshot(setOf(otherId)))

        fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, east4.id)

        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, playerId))
        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, otherId))
        val expectedKan = GameAction.Kan(GameAction.KanType.CLOSED_KAN, east4.id, listOf(east1.id, east2.id, east3.id))
        assertEquals(
            listOf(expectedKan, GameAction.Draw),
            fixtures.eventPublisher.getNotifiedActions(gameId, otherId, playerId),
        )
    }

    /**
     * 驗證加槓時若有其他玩家可以搶槓：開啟 `pendingKanReaction` 反應視窗，副露**未**套用（宣告者手牌
     * 維持原樣、`lastDrawn` 不變）、牌山**未**縮減、只廣播 `Kan`（不廣播 `Draw`，因為嶺上摸牌
     * 尚未真正發生）。
     */
    @Test
    fun `test declare added kan opens chankan window when another player can rob it`() = runTest {
        val fixtures = Fixtures()
        val white1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white4 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val existingPon = Meld(MeldType.PON, listOf(white1, white2, white3), sourceTile = white3, sourceDirection = RelativeDirection.Left)
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon), lastDrawn = white4),
        )
        // 役牌發已成立（1 翻），單騎聽白（搶槓的那張牌），有資格搶槓
        val robberId = Uuid.random()
        val robberTanki = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val robber = FakeMahjongPlayerFactory.create(
            id = robberId,
            initialSeat = Wind.SOUTH,
            hand = Hand(
                tiles = listOf(
                    Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
                    Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3), Tile.Numeric(Tile.Suit.Character, 4),
                    Tile.Numeric(Tile.Suit.Dot, 5), Tile.Numeric(Tile.Suit.Dot, 6), Tile.Numeric(Tile.Suit.Dot, 7),
                    Tile.Numeric(Tile.Suit.Bamboo, 6), Tile.Numeric(Tile.Suit.Bamboo, 7), Tile.Numeric(Tile.Suit.Bamboo, 8),
                ).map { FakeIdentifiedTileFactory.create(it) } + robberTanki,
            ),
            playerRuleState = RiichiPlayerState(),
        )
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber),
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(rinshanTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, white4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val pending = newState.pendingKanReaction
        assertNotNull(pending, "A chankan reaction window should have opened.")
        assertEquals(playerId, pending.declarerId)
        assertEquals(GameAction.Kan(GameAction.KanType.ADDED_KAN, white4.id, emptyList()), pending.kanAction)
        assertEquals(white4, pending.robbedTile)
        assertEquals(setOf(robberId), pending.eligiblePlayerIds)

        val unchangedDeclarer = newState.players.first { it.id == playerId }
        assertEquals(MeldType.PON, unchangedDeclarer.hand.melds.single().type, "The meld must not be upgraded to ADDED_KAN yet.")
        assertEquals(white4, unchangedDeclarer.hand.lastDrawn, "The declarer's hand should be untouched while the window is open.")
        assertEquals(1, newState.tileWall.remainingCount, "The dead wall should not be drawn from yet.")

        val expectedKan = GameAction.Kan(GameAction.KanType.ADDED_KAN, white4.id, emptyList())
        assertEquals(
            listOf(expectedKan),
            fixtures.eventPublisher.getNotifiedActions(gameId, robberId, playerId),
            "Only the Kan declaration should be broadcast; Draw hasn't happened yet.",
        )
    }

    /**
     * 驗證即使某玩家聽牌、本應有資格搶槓，只要已標記為 finished，就不應被納入 `eligiblePlayerIds`，
     * 也就不會開啟搶槓反應視窗。
     */
    @Test
    fun `test declare added kan excludes a finished player from chankan eligibility`() = runTest {
        val fixtures = Fixtures()
        val white1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white4 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val existingPon = Meld(MeldType.PON, listOf(white1, white2, white3), sourceTile = white3, sourceDirection = RelativeDirection.Left)
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon), lastDrawn = white4),
        )
        val finishedRobberId = Uuid.random()
        val finishedRobber = createChankanRobber(finishedRobberId, Wind.SOUTH, Tile.Honor.White)
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, finishedRobber),
            config = RiichiRuleConfig(),
            initialDeadWall = listOf(rinshanTile),
            currentPlayerIndex = 0,
            finishedPlayerIds = setOf(finishedRobberId),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, white4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(newState.pendingKanReaction, "The finished player's chankan eligibility must not open a reaction window.")
    }

    /**
     * 建立一位對 [robbedTile]（役牌發 1 翻 + 單騎聽）有資格搶槓的玩家，供多響搶槓測試共用——
     * 手牌結構與既有的單一搶槓測試相同，只是每次呼叫都用全新的牌實例，讓多位搶槓者可以並存。
     */
    private fun createChankanRobber(id: Uuid, initialSeat: Wind, robbedTileType: Tile): MahjongPlayer {
        val tanki = FakeIdentifiedTileFactory.create(robbedTileType)
        return FakeMahjongPlayerFactory.create(
            id = id,
            initialSeat = initialSeat,
            hand = Hand(
                tiles = listOf(
                    Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
                    Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3), Tile.Numeric(Tile.Suit.Character, 4),
                    Tile.Numeric(Tile.Suit.Dot, 5), Tile.Numeric(Tile.Suit.Dot, 6), Tile.Numeric(Tile.Suit.Dot, 7),
                    Tile.Numeric(Tile.Suit.Bamboo, 6), Tile.Numeric(Tile.Suit.Bamboo, 7), Tile.Numeric(Tile.Suit.Bamboo, 8),
                ).map { FakeIdentifiedTileFactory.create(it) } + tanki,
            ),
            playerRuleState = RiichiPlayerState(),
        )
    }

    /**
     * 驗證雙人同時可搶槓、規則設定為多家和（預設值）時，兩位都留在 `eligiblePlayerIds` 裡——
     * 確認套用 `MultiRonPolicy` 判定後，預設行為與過去（全部開放）維持一致，沒有回歸。
     */
    @Test
    fun `test double chankan opens window for both robbers under default all-winners policy`() = runTest {
        val fixtures = Fixtures()
        val white1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white4 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val existingPon = Meld(MeldType.PON, listOf(white1, white2, white3), sourceTile = white3, sourceDirection = RelativeDirection.Left)
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon), lastDrawn = white4),
        )
        val robber1Id = Uuid.random()
        val robber1 = createChankanRobber(robber1Id, Wind.SOUTH, Tile.Honor.White)
        val robber2Id = Uuid.random()
        val robber2 = createChankanRobber(robber2Id, Wind.WEST, Tile.Honor.White)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber1, robber2),
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(rinshanTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, white4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val pending = fixtures.gameRepo.getTableState(gameId)!!.pendingKanReaction
        assertNotNull(pending)
        assertEquals(setOf(robber1Id, robber2Id), pending.eligiblePlayerIds)
    }

    /**
     * 驗證雙人同時可搶槓、規則設定為頭跳時，只留下順位最接近宣告者下家的那一位。
     */
    @Test
    fun `test double chankan keeps only nearest winner under nearest-winner policy`() = runTest {
        val fixtures = Fixtures()
        val white1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white4 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val existingPon = Meld(MeldType.PON, listOf(white1, white2, white3), sourceTile = white3, sourceDirection = RelativeDirection.Left)
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon), lastDrawn = white4),
        )
        // 座位順序：declarer(EAST) → robber1(SOUTH) → robber2(WEST)，robber1 是離宣告者下家最近者
        val robber1Id = Uuid.random()
        val robber1 = createChankanRobber(robber1Id, Wind.SOUTH, Tile.Honor.White)
        val robber2Id = Uuid.random()
        val robber2 = createChankanRobber(robber2Id, Wind.WEST, Tile.Honor.White)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber1, robber2),
            config = RiichiRuleConfig(
                multiRonPolicy = MultiRonPolicy(
                    doubleRonResolution = RonResolution.NEAREST_WINNER,
                    tripleRonResolution = RonResolution.NEAREST_WINNER,
                ),
            ),
            tileWall = TileWall(listOf(rinshanTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, white4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val pending = fixtures.gameRepo.getTableState(gameId)!!.pendingKanReaction
        assertNotNull(pending)
        assertEquals(setOf(robber1Id), pending.eligiblePlayerIds, "Only the nearest robber should remain eligible.")
    }

    /**
     * 驗證雙人同時可搶槓、規則設定為途中流局時：不開反應視窗、加槓視為未成立（副露維持原本的
     * PON，未升級為 ADDED_KAN，牌山也未被抽取），全員的 `actionHistory` 記錄
     * `ExhaustiveDraw(SanchaHou)`，且事件依序廣播 Kan 再廣播 ExhaustiveDraw。
     */
    @Test
    fun `test double chankan aborts as a draw under abortive-draw policy`() = runTest {
        val fixtures = Fixtures()
        val white1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white4 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val existingPon = Meld(MeldType.PON, listOf(white1, white2, white3), sourceTile = white3, sourceDirection = RelativeDirection.Left)
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon), lastDrawn = white4),
        )
        val robber1Id = Uuid.random()
        val robber1 = createChankanRobber(robber1Id, Wind.SOUTH, Tile.Honor.White)
        val robber2Id = Uuid.random()
        val robber2 = createChankanRobber(robber2Id, Wind.WEST, Tile.Honor.White)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber1, robber2),
            config = RiichiRuleConfig(
                multiRonPolicy = MultiRonPolicy(
                    doubleRonResolution = RonResolution.ABORTIVE_DRAW,
                    tripleRonResolution = RonResolution.ABORTIVE_DRAW,
                ),
            ),
            tileWall = TileWall(listOf(rinshanTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val expectedKan = GameAction.Kan(GameAction.KanType.ADDED_KAN, white4.id, emptyList())
        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, white4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(null, newState.pendingKanReaction, "The multi-ron abortive draw should resolve immediately, not open a window.")
        assertEquals(1, newState.tileWall.remainingCount, "No rinshan tile should be drawn; the kan is voided.")

        val unchangedDeclarer = newState.players.first { it.id == playerId }
        assertEquals(MeldType.PON, unchangedDeclarer.hand.melds.single().type, "The added kan must not be applied.")
        assertEquals(white4, unchangedDeclarer.hand.lastDrawn)

        newState.players.forEach { player ->
            assertEquals(
                GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SanchaHou),
                player.actionHistory.last(),
                "Every player should have the abortive draw recorded.",
            )
        }

        assertEquals(
            listOf(expectedKan, GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SanchaHou)),
            fixtures.eventPublisher.getNotifiedActions(gameId, robber1Id, playerId),
        )
    }

    /**
     * 驗證同一位玩家在搶槓反應視窗開啟期間重複提交宣告（例如雙擊/重試）時回傳
     * [GameError.IllegalAction]，不會覆蓋掉既有的視窗。
     */
    @Test
    fun `test declare kan fails when a chankan window is already pending`() = runTest {
        val fixtures = Fixtures()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val declarer = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = lastDrawn))
        val existingPending = PendingKanReaction(
            declarerId = playerId,
            kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, Uuid.random(), emptyList()),
            robbedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White),
            eligiblePlayerIds = setOf(Uuid.random()),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(declarer), config = RiichiRuleConfig(), currentPlayerIndex = 0)
            .copy(pendingKanReaction = existingPending)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, lastDrawn.id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            GameError.IllegalAction(playerId, gameId, GameAction.Kan(GameAction.KanType.CLOSED_KAN, lastDrawn.id, emptyList())),
            result.error,
        )
    }
}
