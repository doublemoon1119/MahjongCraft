package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStepEvent
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.runAsSingleWrite
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPendingKanDoraReveal
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiWallProgress
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiWallProgress
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeMeldFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.tileRegion
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

    /** 受測對局識別碼。 */
    private val gameId = Uuid.random()

    /** 宣告槓牌的玩家識別碼。 */
    private val playerId = Uuid.random()

    /**
     * 槓牌宣告共用的測試環境。
     *
     * @property gameRepo 保存權威對局的測試倉庫。
     * @property moduleRegistry 已註冊內建規則的中心。
     * @property snapshotRepo 保存玩家可見快照的替身。
     * @property snapshotSynchronizer 更新快照的元件。
     * @property eventPublisher 記錄動作通知的替身。
     * @property presentationPublisher 記錄呈現要求的替身。
     * @property useCase 受測槓牌用例。
     */
    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl(moduleRegistry, componentRegistry = bundledCoreExtensionRegistries().componentRegistry))
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val useCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher, componentRegistry = bundledCoreExtensionRegistries().componentRegistry)
    }

    /** 槓牌成立後補摸的實體一筒。 */
    private val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))

    /**
     * 驗證暗槓成功宣告：手牌 3 張 + 摸到第 4 張同種牌 → 副露正確加入 `exposedMelds`、
     * 手牌對應 4 張牌被移除、`actionHistory` 依序記錄 `Kan` → `Draw`、`lastDrawn` 為補摸的嶺上牌、
     * 牌山正確縮減 1 張。
     *
     * 以目前的遊戲為預期遊戲執行，確認命令只寫入這一局一次（AI 命令的條件式提交契約）。
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
            reservedWallTiles = completeDeadWall(rinshanTile),
            currentPlayerIndex = 0,
            components = riichiTableComponents(potPoints = 0),
        ).withFirstKanPhysicalWallLayout()
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.gameRepo.runAsSingleWrite(gameId) { fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, east4.id) }

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val updated = newState.players.first { it.id == playerId }
        assertTrue(updated.hand.tiles.isEmpty(), "All 4 tiles forming the kan should be removed from standing tiles.")
        val meld = updated.hand.melds.single()
        assertEquals(MeldType.ClosedKan, meld.type)
        assertEquals(setOf(east1, east2, east3, east4), meld.tiles.toSet())
        assertEquals(rinshanTile, updated.hand.lastDrawn, "Should have drawn a replacement tile from the dead wall.")
        assertEquals(
            expected = listOf(GameAction.Kan(GameAction.KanType.CLOSED_KAN, east4.id, listOf(east1.id, east2.id, east3.id)), GameAction.Draw),
            actual = updated.actionHistory.takeLast(2),
            message = "Kan must be recorded before Draw for rinshan kaihou detection to work.",
        )
        assertEquals(table.tileWall.remainingCount - 1, newState.tileWall.remainingCount)
        assertEquals(TurnPhase.AwaitingAction, newState.turnPhase)
        assertEquals(table.tileWall.getAllTiles().last(), newState.reservedWallTiles.last())
        assertEquals(1, newState.riichiWallProgress().completedSupplementalDrawCount)
        // 先呈現暗槓成立（四張移入副露），再呈現補牌到摸牌位與牌牆重排。
        val (declared, drawn) = fixtures.presentationPublisher.getPublishedTableStepBatches(gameId).single()
        listOf(east1, east2, east3, east4).forEach { assertEquals(TileRegion.MELD, declared.tableState.tileRegion(it.id)) }
        assertEquals(TileRegion.RESERVED, declared.tableState.tileRegion(rinshanTile.id))
        assertEquals(TableStepEvent.ActionDeclared(playerId, GameAction.Kan(GameAction.KanType.CLOSED_KAN, east4.id, listOf(east1.id, east2.id, east3.id))), declared.events.first())
        assertEquals(newState, drawn.tableState)
        assertEquals(TileRegion.DRAWN, drawn.tableState.tileRegion(rinshanTile.id))
        drawn.events.filterIsInstance<TableStepEvent.WallRearranged>().single().phases.flatMap { it.moves }.forEach { move ->
            assertEquals(newState.physicalWallLayout?.placements?.get(move.tileId), move.destination)
        }
    }

    /** 公開的暗槓依規則牌序排列（赤五排在同數值普通牌之後），整組牌面公開，兩端朝下。 */
    @Test
    fun `test closed kan follows the rule tile order and keeps both ends face down`() = runTest {
        val fixtures = Fixtures()
        val redFive = FakeIdentifiedTileFactory.create(RiichiTileValues.redFive(Tile.Suit.Dot))
        val five1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))
        val five2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))
        val five3 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(redFive, five1, five2), lastDrawn = five3),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            reservedWallTiles = completeDeadWall(rinshanTile),
            currentPlayerIndex = 0,
            components = riichiTableComponents(potPoints = 0),
        ).withFirstKanPhysicalWallLayout()
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.gameRepo.runAsSingleWrite(gameId) { fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, five3.id) }

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val meld = fixtures.gameRepo.getTableState(gameId)!!.players.first { it.id == playerId }.hand.melds.single()
        assertEquals(listOf(five1, five2, five3, redFive), meld.tiles)
        assertEquals(meld.tiles.map { it.id }.toSet(), meld.publiclyRevealedTileIds)
        assertEquals(setOf(five1.id, redFive.id), meld.faceDownTileIds)
    }

    /** 日麻暗槓的新指示牌在補牌那一步翻開，槓成立那一步還沒有翻開。 */
    @Test
    fun `test closed kan reveals the new indicator in the replacement draw step`() = runTest {
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
            reservedWallTiles = deadWall,
            currentPlayerIndex = 0,
            components = riichiTableComponents(potPoints = 0),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, east4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val currentState = fixtures.gameRepo.getTableState(gameId)!!
        val (kanStep, drawStep) = fixtures.presentationPublisher.getPublishedTableStepBatches(gameId).single()
        assertEquals(table.doraIndicatorIds(), kanStep.tableState.doraIndicatorIds())
        val revealedAtDraw = drawStep.tableState.doraIndicatorIds() - table.doraIndicatorIds()
        assertEquals(currentState.doraIndicatorIds() - table.doraIndicatorIds(), revealedAtDraw)
        assertEquals(1, revealedAtDraw.size, "Exactly one new indicator is revealed by the kan.")
    }

    /** 第二次槓補牌那一步保留第一張已公開的指示牌，並多翻開一張。 */
    @Test
    fun `test second kan keeps the first indicator and reveals one more`() = runTest {
        val fixtures = Fixtures()
        val previousKanTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.South) }
        val eastTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(
                tiles = eastTiles.take(3),
                melds = listOf(
                    element = Meld(
                        type = MeldType.ClosedKan,
                        tiles = previousKanTiles,
                        sourceDirection = RelativeDirection.Self,
                        publiclyRevealedTileIds = previousKanTiles.map { it.id }.toSet(),
                        faceDownTileIds = setOf(previousKanTiles.first().id, previousKanTiles.last().id),
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
            reservedWallTiles = deadWall,
            currentPlayerIndex = 0,
            components = riichiTableComponents(potPoints = 0, wallProgress = RiichiWallProgress(completedSupplementalDrawCount = 1, revealedKanDoraCount = 1)),
        )
        val previouslyVisible = table.riichiWallProgress().getDoraIndicators(table).first.map { it.id }.toSet()
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, eastTiles.last().id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val drawStep = fixtures.presentationPublisher.getPublishedTableStepBatches(gameId).single().last()
        val visibleAtDraw = drawStep.tableState.doraIndicatorIds()
        assertTrue(visibleAtDraw.containsAll(previouslyVisible))
        assertEquals(1, (visibleAtDraw - previouslyVisible).size)
    }

    /** 這份桌況目前翻開的寶牌指示牌。 */
    private fun TableState.doraIndicatorIds(): Set<Uuid> = riichiWallProgress().getDoraIndicators(this).first.map { it.id }.toSet()

    /** 明槓待翻的槓寶牌在下一次暗槓補牌前翻開時，翻開的指示牌記入公開證據。 */
    @Test
    fun `indicator revealed before the next replacement draw is recorded as public evidence`() = runTest {
        val fixtures = Fixtures()
        val openKanTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.South) }
        val eastTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.East) }
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(
                tiles = eastTiles.take(3),
                melds = listOf(FakeMeldFactory.create(type = MeldType.OpenKan, tiles = openKanTiles, sourceTile = openKanTiles.first(), sourceDirection = RelativeDirection.Left)),
                lastDrawn = eastTiles.last(),
            ),
        )
        val pending = RiichiPendingKanDoraReveal(actorPlayerId = playerId, sourceKanType = GameAction.KanType.OPEN_KAN, supplementalDrawNumber = 1)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            reservedWallTiles = List(14) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)) },
            currentPlayerIndex = 0,
            components = riichiTableComponents(
                potPoints = 0,
                wallProgress = RiichiWallProgress(completedSupplementalDrawCount = 1, revealedKanDoraCount = 0, pendingKanDoraReveals = listOf(pending)),
            ),
        )
        val previouslyVisible = table.riichiWallProgress().getDoraIndicators(table).first.map { it.id }.toSet()
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, eastTiles.last().id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val currentState = fixtures.gameRepo.getTableState(gameId)!!
        val newlyVisible = currentState.riichiWallProgress().getDoraIndicators(currentState).first.map { it.id }.toSet() - previouslyVisible
        assertTrue(newlyVisible.isNotEmpty())
        assertTrue(currentState.publicTileEvidenceIds.containsAll(newlyVisible), "$newlyVisible not in ${currentState.publicTileEvidenceIds}")
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
        val existingPon = Meld(MeldType.Pon, listOf(south1, south2, south3), sourceTile = south3, sourceDirection = RelativeDirection.Left, publiclyRevealedTileIds = listOf(south1, south2, south3).map { it.id }.toSet(), faceDownTileIds = emptySet())
        val declarer = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon), lastDrawn = south4),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            reservedWallTiles = completeDeadWall(rinshanTile),
            currentPlayerIndex = 0,
            components = riichiTableComponents(potPoints = 0),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, south4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val updated = newState.players.first { it.id == playerId }
        val meld = updated.hand.melds.single()
        assertEquals(MeldType.AddedKan, meld.type)
        assertEquals(setOf(south1, south2, south3, south4), meld.tiles.toSet())
        assertEquals(south3, meld.sourceTile, "Added kan should preserve the original Pon's source tile.")
        assertEquals(RelativeDirection.Left, meld.sourceDirection, "Added kan should preserve the original Pon's source direction.")
        assertEquals(rinshanTile, updated.hand.lastDrawn)
        assertEquals(
            expected = listOf(GameAction.Kan(GameAction.KanType.ADDED_KAN, south4.id, emptyList()), GameAction.Draw),
            actual = updated.actionHistory.takeLast(2),
        )
    }

    /**
     * 驗證立直後宣告五萬暗槓時，立牌中的四萬會觸發面子結構檢查，宣告遭到拒絕。
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
            components = riichiDeclarationComponents(declarationTile = FakeIdentifiedTileFactory.create(Tile.Honor.North)),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer),
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)), rinshanTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, fifthMan4.id)

        assertTrue(result is Outcome.Error, "Expected Error but got $result")
        assertEquals(
            expected = GameError.IllegalAction(
                playerId = playerId,
                gameId = gameId,
                action = GameAction.Kan(GameAction.KanType.CLOSED_KAN, fifthMan4.id, emptyList()),
            ),
            actual = result.error,
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
            reservedWallTiles = completeDeadWall(rinshanTile),
            currentPlayerIndex = 0,
            components = riichiTableComponents(potPoints = 0),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, eastTiles.first().id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val hand = fixtures.gameRepo.getTableState(gameId)!!.currentPlayer.hand
        assertEquals(MeldType.ClosedKan, hand.melds.single().type)
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
            expected = GameError.IllegalAction(playerId, gameId, GameAction.Kan(GameAction.KanType.CLOSED_KAN, otherTileId, emptyList())),
            actual = result.error,
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
            // （reservedWallTiles，預設空清單）摸盡」是兩回事，tileWall 空的話這次暗槓在走到補摸嶺上牌
            // 之前就會先被 RiichiLegalActionValidator 擋下，這個測試就測不到真正想驗證的情境。
            currentPlayerIndex = 0,
            components = riichiTableComponents(potPoints = 0),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, east4.id)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.WallExhausted(gameId), result.error)
        val unchangedState = fixtures.gameRepo.getTableState(gameId)!!
        val unchangedPlayer = unchangedState.players.first { it.id == playerId }
        assertTrue(unchangedPlayer.hand.melds.isEmpty(), "The meld should not be applied when the replacement draw fails.")
        assertEquals(east4, unchangedPlayer.hand.lastDrawn, "The player's hand should remain exactly as it was before the declaration.")
        assertEquals(0, unchangedState.reservedWallTiles.size, "The rinshan reserve (reservedWallTiles) is what's actually exhausted here.")
        assertTrue(fixtures.presentationPublisher.getPublishedTableStepBatches(gameId).isEmpty())
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
            expected = GameError.IllegalAction(playerId, gameId, GameAction.Kan(GameAction.KanType.OPEN_KAN, lastDrawn.id, emptyList())),
            actual = result.error,
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
            reservedWallTiles = completeDeadWall(rinshanTile),
            currentPlayerIndex = 0,
            components = riichiTableComponents(potPoints = 0),
        )
        fixtures.gameRepo.setTableState(table)
        fixtures.snapshotRepo.setSnapshot(playerId, table.toSnapshot(setOf(playerId)))
        fixtures.snapshotRepo.setSnapshot(otherId, table.toSnapshot(setOf(otherId)))

        fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, east4.id)

        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, playerId))
        assertNotNull(fixtures.snapshotRepo.getSnapshot(gameId, otherId))
        val expectedKan = GameAction.Kan(GameAction.KanType.CLOSED_KAN, east4.id, listOf(east1.id, east2.id, east3.id))
        assertEquals(
            expected = listOf(expectedKan, GameAction.Draw),
            actual = fixtures.eventPublisher.getNotifiedActions(gameId, otherId, playerId),
        )
    }

    /**
     * 驗證加槓時若有其他玩家可以搶槓：開啟 `pendingRobbingReaction` 反應視窗，副露**未**套用（宣告者手牌
     * 維持原樣、`lastDrawn` 不變）、牌山**未**縮減、只廣播 `Kan`（不廣播 `Draw`，因為嶺上摸牌
     * 尚未真正發生）；呈現要求先將加槓牌列入副露，但不包含補牌。
     */
    @Test
    fun `test declare added kan opens chankan window when another player can rob it`() = runTest {
        val fixtures = Fixtures()
        val white1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white4 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val existingPon = Meld(MeldType.Pon, listOf(white1, white2, white3), sourceTile = white3, sourceDirection = RelativeDirection.Left, publiclyRevealedTileIds = listOf(white1, white2, white3).map { it.id }.toSet(), faceDownTileIds = emptySet())
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
            components = riichiDeclarationComponents(),
        )
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber),
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)), rinshanTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, white4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val pending = newState.pendingRobbingReaction
        assertNotNull(pending, "A chankan reaction window should have opened.")
        assertEquals(playerId, pending.declarerId)
        assertEquals(GameAction.Kan(GameAction.KanType.ADDED_KAN, white4.id, emptyList()), pending.declaredAction)
        assertEquals(white4, pending.robbedTile)
        assertEquals(setOf(robberId), pending.eligiblePlayerIds)
        assertEquals(TurnPhase.AwaitingRobbingReaction(pending), newState.turnPhase)

        val unchangedDeclarer = newState.players.first { it.id == playerId }
        assertEquals(MeldType.Pon, unchangedDeclarer.hand.melds.single().type, "The meld must not be upgraded to ADDED_KAN yet.")
        assertEquals(white4, unchangedDeclarer.hand.lastDrawn, "The declarer's hand should be untouched while the window is open.")
        assertEquals(2, newState.tileWall.remainingCount, "The dead wall should not be drawn from yet.")
        assertEquals(setOf(white4.id), newState.publicTileEvidenceIds, "The added tile is public while others decide.")

        val expectedKan = GameAction.Kan(GameAction.KanType.ADDED_KAN, white4.id, emptyList())
        assertEquals(
            expected = listOf(expectedKan),
            actual = fixtures.eventPublisher.getNotifiedActions(gameId, robberId, playerId),
            message = "Only the Kan declaration should be broadcast; Draw hasn't happened yet.",
        )
        // 先呈現加槓完成的候選桌況，權威桌況等反應結束才套用。
        val presented = fixtures.presentationPublisher.getPublishedTableStepBatches(gameId).single().single()
        assertEquals(listOf<TableStepEvent>(TableStepEvent.ActionDeclared(playerId, expectedKan)), presented.events)
        val presentedDeclarer = presented.tableState.players.first { it.id == playerId }
        assertEquals(MeldType.AddedKan, presentedDeclarer.hand.melds.single().type)
        assertEquals(TileRegion.MELD, presented.tableState.tileRegion(white4.id))
        assertNull(presentedDeclarer.hand.lastDrawn)
        assertTrue(presentedDeclarer.hand.tiles.isEmpty())
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
        val existingPon = Meld(MeldType.Pon, listOf(white1, white2, white3), sourceTile = white3, sourceDirection = RelativeDirection.Left, publiclyRevealedTileIds = listOf(white1, white2, white3).map { it.id }.toSet(), faceDownTileIds = emptySet())
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
            reservedWallTiles = completeDeadWall(rinshanTile),
            currentPlayerIndex = 0,
            finishedPlayerIds = setOf(finishedRobberId),
            components = riichiTableComponents(potPoints = 0),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, white4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertNull(newState.pendingRobbingReaction, "The finished player's chankan eligibility must not open a reaction window.")
    }

    /**
     * 建立含發刻子、單騎等待 [robbedTileType] 的搶槓玩家；每次建立新的實體牌，供多家搶槓測試使用。
     *
     * @param id 玩家識別碼。
     * @param initialSeat 玩家初始座位。
     * @param robbedTileType 被搶槓的牌種。
     * @return 具備該牌種單騎搶槓資格的測試玩家。
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
            components = riichiDeclarationComponents(),
        )
    }

    /**
     * 驗證兩人都可搶槓、規則採用預設的多家和政策時，兩人都列入 `eligiblePlayerIds`。
     */
    @Test
    fun `test double chankan opens window for both robbers under default all-winners policy`() = runTest {
        val fixtures = Fixtures()
        val white1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val white4 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val existingPon = Meld(MeldType.Pon, listOf(white1, white2, white3), sourceTile = white3, sourceDirection = RelativeDirection.Left, publiclyRevealedTileIds = listOf(white1, white2, white3).map { it.id }.toSet(), faceDownTileIds = emptySet())
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
            tileWall = TileWall(listOf(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)), rinshanTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, white4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val pending = fixtures.gameRepo.getTableState(gameId)!!.pendingRobbingReaction
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
        val existingPon = Meld(MeldType.Pon, listOf(white1, white2, white3), sourceTile = white3, sourceDirection = RelativeDirection.Left, publiclyRevealedTileIds = listOf(white1, white2, white3).map { it.id }.toSet(), faceDownTileIds = emptySet())
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
            tileWall = TileWall(listOf(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)), rinshanTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, white4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val pending = fixtures.gameRepo.getTableState(gameId)!!.pendingRobbingReaction
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
        val existingPon = Meld(MeldType.Pon, listOf(white1, white2, white3), sourceTile = white3, sourceDirection = RelativeDirection.Left, publiclyRevealedTileIds = listOf(white1, white2, white3).map { it.id }.toSet(), faceDownTileIds = emptySet())
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
            tileWall = TileWall(listOf(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)), rinshanTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val expectedKan = GameAction.Kan(GameAction.KanType.ADDED_KAN, white4.id, emptyList())
        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.ADDED_KAN, white4.id)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(null, newState.pendingRobbingReaction, "The multi-ron abortive draw should resolve immediately, not open a window.")
        assertEquals(2, newState.tileWall.remainingCount, "No rinshan tile should be drawn; the kan is voided.")

        val unchangedDeclarer = newState.players.first { it.id == playerId }
        assertEquals(MeldType.Pon, unchangedDeclarer.hand.melds.single().type, "The added kan must not be applied.")
        assertEquals(white4, unchangedDeclarer.hand.lastDrawn)

        newState.players.forEach { player ->
            assertEquals(
                expected = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SanchaHou),
                actual = player.actionHistory.last(),
                message = "Every player should have the abortive draw recorded.",
            )
        }

        assertEquals(
            expected = listOf(expectedKan, GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.SanchaHou)),
            actual = fixtures.eventPublisher.getNotifiedActions(gameId, robber1Id, playerId),
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
        val existingPending = PendingRobbingReaction(
            declarerId = playerId,
            declaredAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, Uuid.random(), emptyList()),
            robbedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White),
            eligiblePlayerIds = setOf(Uuid.random()),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(declarer), config = RiichiRuleConfig(), currentPlayerIndex = 0)
            .copy(turnPhase = TurnPhase.AwaitingRobbingReaction(existingPending))
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.useCase(gameId, playerId, GameAction.KanType.CLOSED_KAN, lastDrawn.id)

        assertTrue(result is Outcome.Error)
        assertEquals(
            expected = GameError.IllegalAction(playerId, gameId, GameAction.Kan(GameAction.KanType.CLOSED_KAN, lastDrawn.id, emptyList())),
            actual = result.error,
        )
    }

    /**
     * 建立具有完整十四張槽位、且第一張可作為嶺上牌的日麻王牌區。
     *
     * @param firstSupplementalTile 要置於第一個嶺上槽位的牌。
     * @return 首牌為指定摸牌的完整日麻王牌區。
     */
    private fun completeDeadWall(firstSupplementalTile: IdentifiedTile): List<IdentifiedTile> = listOf(firstSupplementalTile) +
        List(13) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)) }
}
