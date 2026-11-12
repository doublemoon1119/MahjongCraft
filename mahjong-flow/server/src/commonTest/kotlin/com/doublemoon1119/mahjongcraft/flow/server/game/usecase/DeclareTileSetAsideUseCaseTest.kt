package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.bundled.BundledRiichiExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.common.game.service.TableStepEvent
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.runAsSingleWrite
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiWallProgress
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.tileRegion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [DeclareTileSetAsideUseCase] 以三人日麻拔北驗證：直接成立、搶北視窗，以及視窗內放過與榮和的結果。 */
class DeclareTileSetAsideUseCaseTest {
    /** 受測對局識別碼。 */
    private val gameId = Uuid.random()

    /** 宣告拔北的玩家。 */
    private val pullerId = Uuid.random()

    /** 可配置為等待北牌的玩家。 */
    private val waiterId = Uuid.random()

    /**
     * 拔北與搶北回應共用的測試環境。
     *
     * @property gameRepo 保存對局的測試倉庫。
     * @property moduleRegistry 內建規則註冊中心。
     * @property snapshotSynchronizer 更新玩家可見快照的元件。
     * @property eventPublisher 記錄流程事件的替身。
     * @property presentationPublisher 記錄呈現要求的替身。
     * @property winPresentationHandoff 暫存和牌呈現要求的元件。
     * @property useCase 受測拔北用例。
     * @property respondToRobbing 處理搶北視窗回應的用例。
     */
    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(moduleRegistry, componentRegistry = bundledCoreExtensionRegistries().componentRegistry))
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val winPresentationHandoff = WinPresentationHandoff()
        val useCase = DeclareTileSetAsideUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher, componentRegistry = bundledCoreExtensionRegistries().componentRegistry)
        val respondToRobbing = RespondToRobbingUseCase(
            gameRepository = gameRepo,
            moduleRegistry = moduleRegistry,
            snapshotSynchronizer = snapshotSynchronizer,
            eventPublisher = eventPublisher,
            presentationPublisher = presentationPublisher,
            winPresentationHandoff = winPresentationHandoff,
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry(bundledCoreExtensionRegistries().componentRegistry).apply {
                BundledRiichiExtension.registerWinSettlementDetailResolvers(this)
                freeze()
            },

            componentRegistry = bundledCoreExtensionRegistries().componentRegistry,
        )
    }

    /** 宣告拔出的實體北牌。 */
    private val north = FakeIdentifiedTileFactory.create(Tile.Honor.North)

    /** 拔北成立後準備補入的實體牌。 */
    private val rinshan = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9))

    /**
     * 驗證沒有人能榮和時，北移到拔北區、補一張嶺上牌，依序記錄拔北與摸牌，並依序呈現拔北與補牌。
     *
     * 以目前的遊戲為預期遊戲執行，確認命令只寫入這一局一次（AI 命令的條件式提交契約）。
     */
    @Test
    fun `pulling north without a robbing chance draws a rinshan tile`() = runTest {
        val fixtures = Fixtures()
        fixtures.gameRepo.setTableState(table(waiter = bystander(waiterId, Wind.SOUTH)))

        val result = fixtures.gameRepo.runAsSingleWrite(gameId) { fixtures.useCase(gameId, pullerId, RIICHI_PULL_NORTH_GAME_ACTION) }

        assertIs<Outcome.Success<Unit>>(result)
        val state = assertNotNull(fixtures.gameRepo.getTableState(gameId))
        val puller = state.players.first { it.id == pullerId }
        assertEquals(listOf(north), puller.tileAreas[RiichiPlayerTileAreaIds.NORTH]?.tiles.orEmpty())
        assertEquals(rinshan, puller.hand.lastDrawn)
        assertTrue(puller.hand.tiles.none { it.id == north.id })
        assertEquals(listOf(RIICHI_PULL_NORTH_GAME_ACTION, GameAction.Draw), puller.actionHistory.takeLast(2))
        assertEquals(1, state.riichiWallProgress().completedNorthDrawCount)
        assertNull(state.pendingRobbingReaction)
        // 先呈現北移到拔北區，再呈現補牌到摸牌位。
        val (pulled, drawn) = fixtures.presentationPublisher.getPublishedTableStepBatches(gameId).single()
        assertEquals(TableStepEvent.ActionDeclared(pullerId, RIICHI_PULL_NORTH_GAME_ACTION), pulled.events.first())
        assertEquals(TileRegion.SET_ASIDE, pulled.tableState.tileRegion(north.id))
        assertEquals(TileRegion.DRAWN, drawn.tableState.tileRegion(rinshan.id))
        assertEquals(state, drawn.tableState)
    }

    /** 驗證手中沒有北時拒絕拔北。 */
    @Test
    fun `pulling north without a north is illegal`() = runTest {
        val fixtures = Fixtures()
        val noNorth = puller().copy(hand = Hand(tiles = listOf(tile(Tile.Numeric(Tile.Suit.Dot, 1))), lastDrawn = tile(Tile.Numeric(Tile.Suit.Dot, 2))))
        fixtures.gameRepo.setTableState(table(puller = noNorth, waiter = bystander(waiterId, Wind.SOUTH)))

        val result = fixtures.useCase(gameId, pullerId, RIICHI_PULL_NORTH_GAME_ACTION)

        assertEquals(Outcome.Error(GameError.IllegalAction(pullerId, gameId, RIICHI_PULL_NORTH_GAME_ACTION)), result)
    }

    /** 驗證等待北牌的玩家取得搶和機會，拔北與補牌暫緩套用，先呈現北已拔出、尚未補牌的樣子。 */
    @Test
    fun `a player waiting on north opens a robbing window`() = runTest {
        val fixtures = Fixtures()
        fixtures.gameRepo.setTableState(table(waiter = northWaiter()))

        val result = fixtures.useCase(gameId, pullerId, RIICHI_PULL_NORTH_GAME_ACTION)

        assertIs<Outcome.Success<Unit>>(result)
        val state = assertNotNull(fixtures.gameRepo.getTableState(gameId))
        val pending = assertNotNull(state.pendingRobbingReaction)
        assertEquals(RIICHI_PULL_NORTH_GAME_ACTION, pending.declaredAction)
        assertEquals(north, pending.robbedTile)
        assertEquals(setOf(waiterId), pending.eligiblePlayerIds)
        val puller = state.players.first { it.id == pullerId }
        assertEquals(north, puller.hand.lastDrawn)
        assertTrue(puller.tileAreas[RiichiPlayerTileAreaIds.NORTH]?.tiles.orEmpty().isEmpty())
        assertEquals(setOf(north.id), state.publicTileEvidenceIds, "The declared north is public while others decide.")
        // 先呈現北已移到拔北區、尚未補牌的候選桌況。
        val presented = fixtures.presentationPublisher.getPublishedTableStepBatches(gameId).single().single()
        assertEquals(listOf<TableStepEvent>(TableStepEvent.ActionDeclared(pullerId, RIICHI_PULL_NORTH_GAME_ACTION)), presented.events)
        assertEquals(TileRegion.SET_ASIDE, presented.tableState.tileRegion(north.id))
        assertNull(presented.tableState.players.first { it.id == pullerId }.hand.lastDrawn)
    }

    /** 驗證全員放過後拔北成立並補牌，放過者記入同巡放過的牌，接續的呈現不再宣告一次拔北。 */
    @Test
    fun `passing the robbing window completes the pull`() = runTest {
        val fixtures = Fixtures()
        fixtures.gameRepo.setTableState(table(waiter = northWaiter()))
        fixtures.useCase(gameId, pullerId, RIICHI_PULL_NORTH_GAME_ACTION)

        val result = fixtures.respondToRobbing(gameId, waiterId, GameAction.Pass)

        assertIs<Outcome.Success<Unit>>(result)
        val state = assertNotNull(fixtures.gameRepo.getTableState(gameId))
        assertNull(state.pendingRobbingReaction)
        val puller = state.players.first { it.id == pullerId }
        assertEquals(listOf(north), puller.tileAreas[RiichiPlayerTileAreaIds.NORTH]?.tiles.orEmpty())
        assertEquals(rinshan, puller.hand.lastDrawn)
        assertTrue(Tile.Honor.North in state.players.first { it.id == waiterId }.passedTilesInRound)
        // 拔北在開啟視窗時已經呈現，這裡只接續補牌，不再宣告一次。
        val resumed = fixtures.presentationPublisher.getPublishedTableStepBatches(gameId).last()
        assertTrue(resumed.flatMap { it.events }.none { it is TableStepEvent.ActionDeclared })
        assertEquals(TileRegion.SET_ASIDE, resumed.first().tableState.tileRegion(north.id))
        assertEquals(TileRegion.DRAWN, resumed.last().tableState.tileRegion(rinshan.id))
    }

    /** 驗證搶北榮和由拔北的玩家支付，拔北不成立，且不算搶槓；被搶的北留在拔北玩家手上但仍公開。 */
    @Test
    fun `robbing the north is paid by the puller without chankan`() = runTest {
        val fixtures = Fixtures()
        fixtures.gameRepo.setTableState(table(waiter = northWaiter()))
        fixtures.useCase(gameId, pullerId, RIICHI_PULL_NORTH_GAME_ACTION)

        val result = fixtures.respondToRobbing(gameId, waiterId, GameAction.Ron(north.id))

        assertIs<Outcome.Success<Unit>>(result)
        val state = assertNotNull(fixtures.gameRepo.getTableState(gameId))
        val puller = state.players.first { it.id == pullerId }
        val waiter = state.players.first { it.id == waiterId }
        assertTrue(waiter.score > INITIAL_SCORE)
        assertEquals(INITIAL_SCORE - (waiter.score - INITIAL_SCORE), puller.score)
        assertTrue(puller.tileAreas[RiichiPlayerTileAreaIds.NORTH]?.tiles.orEmpty().isEmpty())
        assertEquals(north, puller.hand.lastDrawn, "The robbed north stays with the puller because the pull never happened.")
        assertEquals(setOf(north.id), state.publicTileEvidenceIds, "The robbed north stays public after the win.")
        val presentation = assertNotNull(fixtures.winPresentationHandoff.take(gameId, setOf(waiterId)))
        val winner = presentation.settlement.winners.single()
        assertEquals(pullerId, winner.responsiblePlayerId)
        assertEquals(north.id, winner.winningTileId)
        assertTrue(winner.detailFields.toString().contains(RiichiWinSettlementIds.yakuEntry(RiichiYakuType.Ittuitsu, 2).toString()))
        assertTrue(!winner.detailFields.toString().contains("chankan"))
    }

    /**
     * 拔北的玩家：剛摸到北，手中還有兩張北以外的立牌。
     *
     * @return 持有剛摸到的北與兩張立牌的測試玩家。
     */
    private fun puller(): MahjongPlayer = FakeMahjongPlayerFactory.create(
        id = pullerId,
        initialSeat = Wind.EAST,
        hand = Hand(tiles = listOf(tile(Tile.Numeric(Tile.Suit.Dot, 1)), tile(Tile.Numeric(Tile.Suit.Dot, 5))), lastDrawn = north),
        components = riichiDeclarationComponents(),
    ).copy(score = INITIAL_SCORE)

    /**
     * 一筒到九筒加一二三條、單騎等北的玩家（一氣通貫）。
     *
     * @return 以北單騎聽牌的測試玩家。
     */
    private fun northWaiter(): MahjongPlayer = FakeMahjongPlayerFactory.create(
        id = waiterId,
        initialSeat = Wind.SOUTH,
        hand = Hand(
            tiles = (1..9).map { tile(Tile.Numeric(Tile.Suit.Dot, it)) } +
                (1..3).map { tile(Tile.Numeric(Tile.Suit.Bamboo, it)) } +
                tile(Tile.Honor.North),
        ),
        components = riichiDeclarationComponents(),
    ).copy(score = INITIAL_SCORE)

    /**
     * 不會對北有任何反應的玩家。
     *
     * @param id 玩家識別碼。
     * @param seat 初始座位。
     * @return 不會對北作出反應的測試玩家。
     */
    private fun bystander(id: Uuid, seat: Wind): MahjongPlayer = FakeMahjongPlayerFactory.create(
        id = id,
        initialSeat = seat,
        hand = Hand(tiles = listOf(tile(Tile.Numeric(Tile.Suit.Dot, 9)))),
        components = riichiDeclarationComponents(),
    ).copy(score = INITIAL_SCORE)

    /**
     * 建立拔北者目前可行動的三人日麻桌況。
     *
     * @param puller 拔北玩家；預設為持有剛摸到北牌的玩家。
     * @param waiter 可搶北或不作反應的另一位玩家。
     * @return 包含指定補牌、第三位旁觀玩家與預設三人日麻設定的桌況。
     */
    private fun table(
        puller: MahjongPlayer = puller(),
        waiter: MahjongPlayer,
    ): TableState = FakeTableStateFactory.create(
        id = gameId,
        players = listOf(puller, waiter, bystander(Uuid.random(), Wind.WEST)),
        config = ThreePlayerRiichiRuleConfig(),
        reservedWallTiles = listOf(rinshan) + List(13) { tile(Tile.Honor.White) },
        currentPlayerIndex = 0,
        components = riichiTableComponents(potPoints = 0),
    )

    /**
     * 建立具有測試識別碼的實體牌。
     *
     * @param tile 要建立的牌種。
     * @return 該牌種的新實體牌。
     */
    private fun tile(tile: Tile): IdentifiedTile = FakeIdentifiedTileFactory.create(tile)

    private companion object {
        /** 各測試玩家的起始分數。 */
        const val INITIAL_SCORE = 35000
    }
}
