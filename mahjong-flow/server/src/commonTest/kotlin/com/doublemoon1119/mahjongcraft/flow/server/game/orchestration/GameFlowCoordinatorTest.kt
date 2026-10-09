package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.RoundPreparationAiContext
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.bundled.BundledRiichiExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.ExpectedGameWrittenTwiceException
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.ExhaustiveDrawSettlementPresentationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAbortiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolvePostReactionRoundOutcomeUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolveWinRoundContinuationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToRobbingUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ReturnToRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SubmitRoundPreparationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.WinPresentationSegment
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * [GameFlowCoordinator] 的單元測試類別。
 *
 * 驗證三種自動銜接時機：一般流局（[GameError.WallExhausted]）、四槓散了（攔截
 * [GameCommand.Discard]/[GameCommand.Riichi]）、連莊/過莊（是否結束本局的判斷），
 * 以及不該誤觸發的一般路徑與錯誤原樣傳遞。
 */
class GameFlowCoordinatorTest {

    private val gameId = Uuid.random()

    /**
     * 協調者與它依賴的流程服務。
     *
     * @param winRoundContinuationResolverRegistry 胡牌後是否繼續本局的解析器。
     * @param extraStrategies 另外登記的 AI 策略，以策略 key 索引。
     * @param roundPreparationResolvers 開局準備的解析器；提供時才接上開局準備的提交與 AI。
     * @param extraCommandHandlers 另外登記擴充命令 handler 的方式。
     */
    private class Fixtures(
        winRoundContinuationResolverRegistry: WinRoundContinuationResolverRegistry = WinRoundContinuationResolverRegistry().apply { freeze() },
        extraStrategies: Map<String, MahjongAiStrategy> = emptyMap(),
        roundPreparationResolvers: RoundPreparationResolverRegistry? = null,
        extraCommandHandlers: (ExtensionGameCommandExecutorRegistry) -> Unit = {},
    ) {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl(moduleRegistry))
        val handSortPreferenceStore = HandSortPreferenceStore()
        val postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply {
            BundledRiichiExtension.registerPostActionExhaustiveDrawResolvers(this)
            freeze()
        }
        val winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry().apply {
            BundledRiichiExtension.registerWinSettlementDetailResolvers(this)
            freeze()
        }
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val winPresentationHandoff = WinPresentationHandoff()
        val presentationBusyGate = FakeGamePresentationBusyGate()
        val extensionCommandExecutor = ExtensionGameCommandExecutor(
            registry = ExtensionGameCommandExecutorRegistry().apply {
                BundledRiichiExtension.registerGameCommandHandlers(this)
                extraCommandHandlers(this)
                freeze()
            },
            context = ExtensionGameCommandContext(
                gameRepository = gameRepo,
                moduleRegistry = moduleRegistry,
                snapshotSynchronizer = snapshotSynchronizer,
                handSortPreferenceStore = handSortPreferenceStore,
                postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolverRegistry,
                eventPublisher = eventPublisher,
                presentationPublisher = presentationPublisher,
            ),
        )
        val router = GameActionRouter(
            drawTileUseCase = DrawTileUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
            discardTileUseCase = DiscardTileUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                postActionExhaustiveDrawResolverRegistry,
                eventPublisher,
                presentationPublisher,
            ),
            declareTsumoUseCase = DeclareTsumoUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                eventPublisher,
                presentationPublisher,
                winPresentationHandoff,
                winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
                winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
            ),
            declareKanUseCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
            respondToDiscardUseCase = RespondToDiscardUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                eventPublisher,
                presentationPublisher,
                winPresentationHandoff,
                winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
                winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
                postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolverRegistry,
            ),
            respondToRobbingUseCase = RespondToRobbingUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                eventPublisher,
                presentationPublisher,
                winPresentationHandoff,
                winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
                winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
            ),
            declareAbortiveDrawUseCase = DeclareAbortiveDrawUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            extensionCommandExecutor = extensionCommandExecutor,
            submitRoundPreparationUseCase = roundPreparationResolvers?.let { SubmitRoundPreparationUseCase(gameRepo, moduleRegistry, it, snapshotSynchronizer) },
        )
        val getLegalActionsUseCase = GetLegalActionsUseCase(gameRepo, moduleRegistry)
        val aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = RandomAiStrategy.KEY).apply {
            registerBuiltInAiStrategies(moduleRegistry, ExtensionGameActionAiRegistry(moduleRegistry), OpponentModelRegistry().apply { BundledRiichiExtension.registerOpponentModels(this) })
            extraStrategies.forEach { (key, strategy) -> register(key) { strategy } }
        }
        val aiTurnDriver = AiTurnDriver(gameRepo, getLegalActionsUseCase, aiStrategyRegistry, GameVisibilityPolicyImpl(moduleRegistry), moduleRegistry, AiDecisionExecutor.direct())
        val clock = MutableMonotonicClock()
        val decisionTimerManager = GameDecisionTimerManager(
            gameRepository = gameRepo,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(clock),
            clock = clock,
        )
        val coordinator = GameFlowCoordinator(
            gameActionRouter = router,
            gameRepository = gameRepo,
            moduleRegistry = moduleRegistry,
            winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
            declareExhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher),
            resolvePostReactionRoundOutcomeUseCase = ResolvePostReactionRoundOutcomeUseCase(
                gameRepo,
                moduleRegistry,
                PostReactionRoundOutcomeResolverRegistry().apply { freeze() },
                snapshotSynchronizer,
                winSettlementDetailResolverRegistry,
            ),
            resolveWinRoundContinuationUseCase = ResolveWinRoundContinuationUseCase(
                gameRepo,
                moduleRegistry,
                winRoundContinuationResolverRegistry,
                snapshotSynchronizer,
            ),
            advanceRoundUseCase = AdvanceRoundUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                eventPublisher,
                presentationPublisher,
            ),
            // FakeGameRepository 是獨立於 AuthoritativeStateStore 的測試替身，這裡的 ReturnToRoomUseCase
            // 因此接不到同一份對局資料，對本檔案的測試而言只是滿足建構子的無害 no-op；真的驗證
            // Game → Room 轉移的整合測試見 ReturnToRoomUseCaseTest／MahjongAutoDrawServiceTest 那種
            // 共用真正 AuthoritativeStateStore 的 Fixtures 寫法。
            returnToRoomUseCase = ReturnToRoomUseCase(AuthoritativeStateStore(), FakeRoomSnapshotRepository(), FakeRoomEventPublisher(), presentationPublisher),
            aiTurnDriver = aiTurnDriver,
            forcedAutoPlayDriver = ForcedAutoPlayDriver(gameRepo),
            decisionAvailabilityService = GameDecisionAvailabilityService(
                presentationBusyGate,
                decisionTimerManager,
                DecisionTimerSynchronizationService(
                    decisionTimerManager,
                    gameRepo,
                    FakeDecisionTimerUpdatePublisher(),
                ),
            ),
            presentationBusyGate = presentationBusyGate,
            exhaustiveDrawSettlementPresentationService = ExhaustiveDrawSettlementPresentationService(presentationPublisher),
            winPresentationHandoff = winPresentationHandoff,
            presentationPublisher = presentationPublisher,
            roundPreparationAiDriver = roundPreparationResolvers?.let {
                RoundPreparationAiDriver(gameRepo, moduleRegistry, it, aiStrategyRegistry, GameVisibilityPolicyImpl(moduleRegistry), AiDecisionExecutor.direct())
            },
        )
    }

    /** 驗證進入強制自動操作後不再接受玩家手動命令。 */
    @Test
    fun `test forced auto play player command is rejected`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                id = gameId,
                players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
            ),
            flowConfig = GameFlowConfig(),
            forcedAutoPlayPlayerIds = setOf(playerId),
        )
        fixtures.gameRepo.setGame(game)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, playerId, GameCommand.Draw)

        assertEquals(
            Outcome.Error(GameError.ForcedAutoPlayActive(playerId, gameId)),
            result,
        )
        assertEquals(game.tableState, fixtures.gameRepo.getTableState(gameId))
    }

    /**
     * 迴歸測試：驗證強制自動操作只鎖住逾時當下那一次決策——[GameFlowCoordinator.driveAutomatedPlayers]
     * 替強制自動操作玩家送出一次自動捨牌後，該玩家必須立刻從
     * [Game.forcedAutoPlayPlayerIds] 移除，而不是被永久鎖住到對局結束（曾經的設計缺陷：一旦逾時，
     * 玩家之後每一次決策都會被伺服器代打，完全拿不回操作權）。
     */
    @Test
    fun `test forced auto play only locks the single timed out decision`() = runTest {
        val fixtures = Fixtures()
        val forcedPlayerId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val forcedPlayer = FakeMahjongPlayerFactory.create(
            id = forcedPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = lastDrawn),
        )
        val other = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val game = Game(
            tableState = FakeTableStateFactory.create(
                id = gameId,
                players = listOf(forcedPlayer, other),
                config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
                currentPlayerIndex = 0,
            ),
            flowConfig = GameFlowConfig(),
            forcedAutoPlayPlayerIds = setOf(forcedPlayerId),
        )
        fixtures.gameRepo.setGame(game)

        fixtures.coordinator.driveAutomatedPlayers(gameId)

        val updatedGame = fixtures.gameRepo.getGame(gameId)!!
        assertTrue(
            forcedPlayerId !in updatedGame.forcedAutoPlayPlayerIds,
            "Player must regain control after their single missed decision is auto-played.",
        )
        assertEquals(lastDrawn, updatedGame.tableState.players.first { it.id == forcedPlayerId }.discardPile.entries.single().tile)
    }

    /** 驗證單步入口只執行一個自動命令，不會在同一次呼叫中遞迴驅動後續玩家。 */
    @Test
    fun `test advance automated player step performs one automatic action`() = runTest {
        val fixtures = Fixtures()
        val forcedPlayerId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val forcedPlayer = FakeMahjongPlayerFactory.create(
            id = forcedPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = lastDrawn),
        )
        val ai = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(forcedPlayer, ai),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            tileWall = TileWall(List(20) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9)) }),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setGame(
            Game(
                tableState = table,
                flowConfig = GameFlowConfig(),
                forcedAutoPlayPlayerIds = setOf(forcedPlayerId),
                aiPlayerStrategyKeys = mapOf(ai.id to RandomAiStrategy.KEY),
            ),
        )

        assertTrue(fixtures.coordinator.advanceAutomatedPlayerStep(gameId))

        val updated = fixtures.gameRepo.getGame(gameId)!!
        assertTrue(forcedPlayerId !in updated.forcedAutoPlayPlayerIds, "The forced decision should be cleared.")
        assertEquals(1, updated.tableState.players.first { it.id == forcedPlayerId }.discardPile.entries.size)
        assertTrue(
            updated.tableState.players.first { it.id == ai.id }.discardPile.entries.isEmpty(),
            "A single step must not execute the following AI decision.",
        )
    }

    /** 驗證呈現忙碌時單步入口回傳無進展，且不會修改權威桌況。 */
    @Test
    fun `test advance automated player step returns false while presentation is busy`() = runTest {
        val fixtures = Fixtures()
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player))
        fixtures.gameRepo.setTableState(table)
        fixtures.presentationBusyGate.setBusy(gameId, true)

        assertEquals(false, fixtures.coordinator.advanceAutomatedPlayerStep(gameId))
        assertEquals(table, fixtures.gameRepo.getTableState(gameId))
    }

    // ---- 一般流局：WallExhausted 銜接 ----

    /**
     * 驗證任一命令回傳 [GameError.WallExhausted] 時，立即銜接一般流局並接著開下一局：
     * 呼叫端仍然看到原始的 `WallExhausted` 錯誤，但桌況已經自動流局、重新發牌。
     */
    @Test
    fun `test wall exhausted chains exhaustive draw and advance round`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val otherPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player, otherPlayer),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, playerId, GameCommand.Draw)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.WallExhausted(gameId), result.error)
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.tileWall.remainingCount > 0, "The wall should have been rebuilt for the next hand.")
        assertTrue(newState.players.first { it.id == playerId }.actionHistory.isEmpty(), "A fresh hand's actionHistory should be empty.")
    }

    /**
     * 驗證規則不支援一般流局結算時（`declareExhaustiveDraw` 回傳 null），不會誤觸發
     * `AdvanceRoundUseCase`——桌況除了 `WallExhausted` 錯誤本身以外完全不變。
     */
    @Test
    fun `test wall exhausted does not chain advance round when exhaustive draw is unsupported`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = TaiwanRuleConfig(),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, playerId, GameCommand.Draw)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.WallExhausted(gameId), result.error)
        assertEquals(table, fixtures.gameRepo.getTableState(gameId))
    }

    // ---- 四槓散了：捨牌後收斂 ----

    private fun kanMeldsOf(vararg tileValues: Tile): List<Meld> = tileValues.map { tile ->
        val tiles = List(4) { FakeIdentifiedTileFactory.create(tile) }
        Meld(MeldType.CLOSED_KAN, tiles, sourceTile = null, sourceDirection = RelativeDirection.Self)
    }

    private fun suukanNagareTable(dealerId: Uuid, otherId: Uuid, dealerLastDrawn: IdentifiedTile): TableState {
        val completedKan = GameAction.Kan(
            type = GameAction.KanType.CLOSED_KAN,
            tileId = Uuid.random(),
            withTiles = List(3) { Uuid.random() },
        )
        val dealer = FakeMahjongPlayerFactory.create(
            id = dealerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = kanMeldsOf(Tile.Honor.East, Tile.Honor.South), lastDrawn = dealerLastDrawn),
        ).recordAction(completedKan).recordAction(GameAction.Draw)
        val other = FakeMahjongPlayerFactory.create(
            id = otherId,
            initialSeat = Wind.SOUTH,
            hand = Hand(melds = kanMeldsOf(Tile.Honor.West, Tile.Honor.North)),
        )
        return FakeTableStateFactory.create(
            id = gameId,
            players = listOf(dealer, other),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            currentPlayerIndex = 0,
        )
    }

    /**
     * 驗證四槓散了成立（4 個槓子分屬不同玩家）時，[GameCommand.Discard] 會先正常執行，再由通用
     * 本局結束流程收斂並開下一局；莊家固定連莊（`comboCount + 1`、莊家方位不變）。
     */
    @Test
    fun `test completed discard is followed by suukan nagare when pending`() = runTest {
        val fixtures = Fixtures()
        val dealerId = Uuid.random()
        val otherId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        fixtures.gameRepo.setTableState(suukanNagareTable(dealerId, otherId, lastDrawn))

        val result = fixtures.coordinator.dispatchThenDrive(gameId, dealerId, GameCommand.Discard(lastDrawn.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(1, newState.comboCount, "Suukan nagare is an abortive draw; the dealer always repeats.")
        assertEquals(Wind.EAST, newState.players.first { it.id == dealerId }.seatWind)
        assertTrue(newState.players.first { it.id == dealerId }.hand.melds.isEmpty(), "A fresh hand should have no melds left over.")
    }

    /**
     * 驗證槓子總數未滿 4 個時，四槓散了不成立，[GameCommand.Discard] 正常執行（不被攔截）。
     */
    @Test
    fun `test discard command proceeds normally when suukan nagare is not pending`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = lastDrawn))
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, playerId, GameCommand.Discard(lastDrawn.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount, "No hand-ending event occurred; the round should not have advanced.")
        assertEquals(lastDrawn, newState.players.first { it.id == playerId }.discardPile.entries.single().tile)
    }

    // ---- 連莊/過莊：一定結束本局的命令 ----

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

    /**
     * 驗證自摸成功一定結束本局，`AdvanceRoundUseCase` 會被銜接（新的一手牌已重新發好、
     * `actionHistory` 已重置）。
     */
    @Test
    fun `test tsumo command chains advance round`() = runTest {
        val fixtures = Fixtures()
        val winnerId = Uuid.random()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = daisangenTiles.map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = winningTile),
            discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        ).copy(score = 25000)
        val other = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH, playerRuleState = RiichiPlayerState()).copy(score = 25000)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(winner, other), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, winnerId, GameCommand.Tsumo)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.players.first { it.id == winnerId }.actionHistory.isEmpty(), "A fresh hand's actionHistory should be empty.")
        assertEquals(1, newState.comboCount, "The winner is the dealer, so the dealer should repeat.")
        // 胡牌演出由 use case 交給 handoff、再由 coordinator 在取得 EndRound 後立即發布到所有玩家共用
        // 時間軸——這是既有規則一直以來的可觀察行為，只是呼叫點從 use case 內部移到了這裡。
        val celebrations = fixtures.presentationPublisher.getPublishedWinCelebrations(gameId)
        assertEquals(1, celebrations.size)
        assertEquals(winningTile.id, celebrations.single().winningTileId)
        assertTrue(celebrations.single().isTsumo)
        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertTrue(!published.roundContinues, "The round ended, so the whole presentation blocks every player.")
        assertEquals(
            listOf(listOf(WinPresentationSegment.CELEBRATION, WinPresentationSegment.SETTLEMENT)),
            fixtures.presentationPublisher.getWinPresentationSegmentOrder(gameId),
            "Celebration must always be ordered before settlement.",
        )
        assertEquals(null, fixtures.winPresentationHandoff.take(gameId, setOf(winnerId)), "The handoff must be consumed.")
    }

    /**
     * 驗證規則模組登記了 [WinRoundContinuationResolver] 且回傳 [WinRoundDirective.ContinueRound]
     * 時，自摸不會結束本局：不銜接 `AdvanceRoundUseCase`（`pendingTransition` 維持 null、贏家的
     * `Tsumo` 記錄原樣保留，不會被新一手牌重置），改為原子套用 `finishedPlayerIds`／
     * `currentPlayerIndex` 的變化。
     */
    @Test
    fun `test tsumo command does not chain advance round when a resolver returns ContinueRound`() = runTest {
        val winnerId = Uuid.random()
        val otherId = Uuid.random()
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val fixtures = runContinuingWinTsumo(
            winnerId = winnerId,
            otherId = otherId,
            winningTile = winningTile,
            settlementDetail = ContinuingWinSettlementDetail.WINNER_DETAILS,
        )
        val game = fixtures.gameRepo.getGame(gameId)!!
        assertEquals(null, game.pendingTransition, "ContinueRound must not chain AdvanceRound.")
        val newState = game.tableState
        assertTrue(
            newState.players.first { it.id == winnerId }.actionHistory.any { it is GameAction.Tsumo },
            "The hand did not end, so the Tsumo record must not be reset by a fresh deal.",
        )
        assertEquals(setOf(winnerId), newState.finishedPlayerIds)
        assertEquals(1, newState.currentPlayerIndex, "Turn should be handed to nextPlayerId from the directive.")

        // FULL 模式：演出照樣發布，但改走中途胡牌專用時間軸——它不列入阻擋所有玩家的忙碌判定，因此其他仍在
        // 本局中的玩家可以繼續摸打，只有換局要等它播完。
        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertTrue(published.roundContinues, "A continuing win must tell the platform the round goes on.")
        assertTrue(
            published.celebration.winners.any { it.cueIds.isNotEmpty() },
            "Daisangen is a yakuman, so this continuing win still carries a showcase reason.",
        )
        assertEquals(setOf(winnerId), published.winnerPlayerIds)
        assertEquals(
            listOf(listOf(WinPresentationSegment.CELEBRATION, WinPresentationSegment.SETTLEMENT)),
            fixtures.presentationPublisher.getWinPresentationSegmentOrder(gameId),
            "Celebration must always be ordered before settlement.",
        )
        assertEquals(null, fixtures.winPresentationHandoff.take(gameId, setOf(winnerId)), "The handoff must be consumed.")

        // 中途胡牌演出還在播時本局不該換局；播完後才會真的推進。
        fixtures.gameRepo.updateGame(gameId) { current ->
            current!!.copy(pendingTransition = PendingGameTransition.AdvanceRound) to Unit
        }
        fixtures.presentationBusyGate.setPresentingContinuingWin(gameId, true)
        assertTrue(!fixtures.coordinator.resumePendingGameTransition(gameId))
        fixtures.presentationBusyGate.setPresentingContinuingWin(gameId, false)
        assertTrue(
            fixtures.coordinator.resumePendingGameTransition(gameId),
            "Once the queue drains, the pending round transition should finally run.",
        )
    }

    /**
     * 驗證 [ContinuingWinSettlementDetail.SCORE_CHANGES_ONLY]：胡牌演出**完全照常**（理牌、攤牌、降臨特效，役滿時還有
     * showcase），只有結算面板換成精簡版。
     *
     * 這正是這組 enum 的設計重點：胡牌演出是「這個人胡了、退出本局」在世界裡唯一的視覺訊號，其他仍在
     * 局中的玩家必須看見，任何模式都不可省略；真正依情境調整的只有面板，因為面板是要**讀**的。
     */
    @Test
    fun `test continuing win in brief mode only shrinks the settlement panel`() = runTest {
        val fixtures = runContinuingWinTsumo(
            winnerId = Uuid.random(),
            otherId = Uuid.random(),
            winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red),
            settlementDetail = ContinuingWinSettlementDetail.SCORE_CHANGES_ONLY,
        )

        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertFalse(published.settlement.includesWinnerDetails, "SCORE_CHANGES_ONLY must leave out the winner details.")
        assertTrue(published.roundContinues)
        assertEquals(
            listOf(listOf(WinPresentationSegment.CELEBRATION, WinPresentationSegment.SETTLEMENT)),
            fixtures.presentationPublisher.getWinPresentationSegmentOrder(gameId),
            "The win celebration is never skipped, whatever the settlement mode.",
        )
        // 手牌是大三元役滿：即使面板精簡，仍帶著展示理由。
        assertTrue(
            published.celebration.winners.any { it.cueIds.isNotEmpty() },
            "A yakuman showcase reason must still be published even when the panel is brief.",
        )
    }

    // 234m 567m 234p 567p 5s（斷么九，13 張立牌）——一般役，**不是**役滿，因此不會有 showcase cue。
    private val tanyaoTiles = listOf(
        Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3), Tile.Numeric(Tile.Suit.Character, 4),
        Tile.Numeric(Tile.Suit.Character, 5), Tile.Numeric(Tile.Suit.Character, 6), Tile.Numeric(Tile.Suit.Character, 7),
        Tile.Numeric(Tile.Suit.Dot, 2), Tile.Numeric(Tile.Suit.Dot, 3), Tile.Numeric(Tile.Suit.Dot, 4),
        Tile.Numeric(Tile.Suit.Dot, 5), Tile.Numeric(Tile.Suit.Dot, 6), Tile.Numeric(Tile.Suit.Dot, 7),
        Tile.Numeric(Tile.Suit.Bamboo, 5),
    )

    /** 驗證**一般**（非役滿）中途胡牌沒有展示理由，平台不需要暫停其他仍在本局中的玩家。 */
    @Test
    fun `test ordinary continuing win does not need to pause the other players`() = runTest {
        val fixtures = runContinuingWinTsumo(
            winnerId = Uuid.random(),
            otherId = Uuid.random(),
            winningTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
            settlementDetail = ContinuingWinSettlementDetail.WINNER_DETAILS,
            handTiles = tanyaoTiles,
        )

        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertTrue(published.roundContinues)
        val celebration = assertNotNull(published.celebration)
        assertTrue(
            celebration.winners.all { it.cueIds.isEmpty() },
            "Tanyao is not a yakuman, so no showcase reason should be resolved.",
        )
    }

    /**
     * 驗證含役滿的中途胡牌帶著展示理由——平台據此暫停玩家／AI／強制自動操作／決策計時器直到展示播完
     * （見 [WinPresentationRequest] KDoc）。
     *
     * 跟上一個測試的唯一差別就是手牌：大三元是役滿，只有役滿成立時才有展示理由。
     */
    @Test
    fun `test continuing win carrying a yakuman publishes its showcase reason`() = runTest {
        val fixtures = runContinuingWinTsumo(
            winnerId = Uuid.random(),
            otherId = Uuid.random(),
            winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red),
            settlementDetail = ContinuingWinSettlementDetail.WINNER_DETAILS,
        )

        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertTrue(published.roundContinues, "The round continues, so most of this presentation must not block.")
        val celebration = assertNotNull(published.celebration)
        assertEquals(
            listOf("mahjongcraft:riichi/yakuman/daisangen"),
            celebration.winners.single().cueIds,
            "Daisangen is a yakuman, so the celebration must carry its showcase reason.",
        )
    }

    /**
     * 驗證同一局內連續兩次中途胡牌各自送出一次完整呈現請求，順序與贏家都正確——排隊播放本身由平台的
     * 呈現時間軸負責（見 `FabricGamePresentationPublisher`），這裡驗證的是 flow 層確實逐次送出、
     * 沒有把兩次合併或漏掉任何一次。
     */
    @Test
    fun `test consecutive continuing wins each publish their own presentation in order`() = runTest {
        val firstWinnerId = Uuid.random()
        val secondWinnerId = Uuid.random()
        val fixtures = runContinuingWinTsumo(
            winnerId = firstWinnerId,
            otherId = secondWinnerId,
            winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red),
            settlementDetail = ContinuingWinSettlementDetail.WINNER_DETAILS,
        )
        // 第二位玩家接著自摸：沿用同一份 fixtures（含同一個 resolver registry）再跑一次。
        val secondWinningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        fixtures.gameRepo.updateGame(gameId) { game ->
            val state = game!!.tableState
            val updated = state.players.map { player ->
                if (player.id == secondWinnerId) {
                    player.copy(hand = Hand(tiles = daisangenTiles.map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = secondWinningTile))
                } else {
                    player
                }
            }
            game.copy(tableState = state.copy(players = updated)) to Unit
        }

        fixtures.coordinator.dispatchThenDrive(gameId, secondWinnerId, GameCommand.Tsumo)

        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId)
        assertEquals(2, published.size, "Each continuing win must publish its own presentation, never merged.")
        assertEquals(setOf(firstWinnerId), published[0].winnerPlayerIds)
        assertEquals(setOf(secondWinnerId), published[1].winnerPlayerIds)
        assertEquals(
            List(2) { listOf(WinPresentationSegment.CELEBRATION, WinPresentationSegment.SETTLEMENT) },
            fixtures.presentationPublisher.getWinPresentationSegmentOrder(gameId),
            "Celebration must precede settlement within every publish.",
        )
    }

    /**
     * 以指定的 [settlementDetail] 跑一次「規則判定本局繼續」的自摸，回傳執行後的 fixtures 供斷言。
     *
     * 兩種結算面板模式的測試共用同一份桌況與 resolver 設定，差別只在 [settlementDetail]。
     */
    private suspend fun runContinuingWinTsumo(
        winnerId: Uuid,
        otherId: Uuid,
        winningTile: IdentifiedTile,
        settlementDetail: ContinuingWinSettlementDetail,
        handTiles: List<Tile> = daisangenTiles,
    ): Fixtures {
        val ruleModuleId = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }.getModule(RiichiRuleConfig()).id
        val continuationRegistry = WinRoundContinuationResolverRegistry().apply {
            register(
                object : WinRoundContinuationResolver {
                    override val id: String = "test:continue"
                    override val ruleModuleId: String = ruleModuleId
                    override val priority: Int = 0

                    override fun resolve(
                        context: WinRoundContinuationContext,
                        ruleModule: MahjongRuleModule<*>,
                    ): WinRoundDirective {
                        // 從 context 取本次贏家，讓同一份 registry 能重複用於同一局內的連續胡牌。
                        val settled = context.settledTableState
                        val nextActive = settled.players
                            .first { it.id !in context.winnerPlayerIds && settled.isPlayerActive(it.id) }
                        return WinRoundDirective.ContinueRound(
                            newlyFinishedPlayerIds = context.winnerPlayerIds,
                            nextPlayerId = nextActive.id,
                            settlementDetail = settlementDetail,
                        )
                    }
                },
            )
            freeze()
        }
        val fixtures = Fixtures(winRoundContinuationResolverRegistry = continuationRegistry)
        val winner = FakeMahjongPlayerFactory.create(
            id = winnerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = handTiles.map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = winningTile),
            discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        ).copy(score = 25000)
        val other = FakeMahjongPlayerFactory.create(id = otherId, initialSeat = Wind.SOUTH, playerRuleState = RiichiPlayerState()).copy(score = 25000)
        // 三人桌：連續兩次中途胡牌之後仍必須留下至少一位 active 玩家，否則 ContinueRound 不合法。
        val bystander = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST, playerRuleState = RiichiPlayerState()).copy(score = 25000)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(winner, other, bystander),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, winnerId, GameCommand.Tsumo)
        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        return fixtures
    }

    /**
     * 驗證九種九牌宣告成功一定結束本局，`AdvanceRoundUseCase` 會被銜接。
     */
    @Test
    fun `test kyuushu kyuuhai command chains advance round`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        // 9 種以上么九牌：1m/9m/1p/9p/1s/9s/東/南/西
        val kyuushuTiles = listOf(
            Tile.Numeric(Tile.Suit.Character, 1), Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Dot, 1), Tile.Numeric(Tile.Suit.Dot, 9),
            Tile.Numeric(Tile.Suit.Bamboo, 1), Tile.Numeric(Tile.Suit.Bamboo, 9),
            Tile.Honor.East, Tile.Honor.South, Tile.Honor.West,
            Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3),
            Tile.Numeric(Tile.Suit.Character, 4), Tile.Numeric(Tile.Suit.Character, 5),
        )
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.North)
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = kyuushuTiles.map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = drawn),
        )
        val otherPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player, otherPlayer),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, playerId, GameCommand.DeclareExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.players.first { it.id == playerId }.actionHistory.isEmpty())
        assertEquals(1, newState.comboCount, "Kyuushu kyuuhai is an abortive draw; the dealer always repeats.")
    }

    // ---- 連莊/過莊：視分支結果的命令 ----

    /**
     * 驗證一般捨牌、無人反應時不會結束本局，`AdvanceRoundUseCase` 不會被誤觸發。
     */
    @Test
    fun `test ordinary discard does not chain advance round`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val bystanderId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = lastDrawn))
        val bystander = FakeMahjongPlayerFactory.create(id = bystanderId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(player, bystander), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, playerId, GameCommand.Discard(lastDrawn.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount)
        assertEquals(GameAction.Discard(lastDrawn.id), newState.players.first { it.id == playerId }.actionHistory.last())
    }

    /**
     * 驗證捨牌觸發內嵌的四風連打時，`AdvanceRoundUseCase` 會被銜接。
     */
    @Test
    fun `test discard triggering suufon renda chains advance round`() = runTest {
        val fixtures = Fixtures()
        val p1Id = Uuid.random()
        val p2Id = Uuid.random()
        val p1FirstDiscard = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val p2LastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val p1 = FakeMahjongPlayerFactory.create(
            id = p1Id,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(p1FirstDiscard),
        )
        val p2 = FakeMahjongPlayerFactory.create(
            id = p2Id,
            initialSeat = Wind.SOUTH,
            hand = Hand(lastDrawn = p2LastDrawn),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(p1, p2), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 1)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, p2Id, GameCommand.Discard(p2LastDrawn.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.players.first { it.id == p2Id }.actionHistory.isEmpty())
        assertEquals(1, newState.comboCount, "Suufon renda is an abortive draw; the dealer always repeats.")
    }

    private fun discardReactionTable(discarderId: Uuid, respondentId: Uuid, respondentHand: Hand): TableState {
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        // score 給一個一般起始分數（而非工廠預設的 0），避免放銃付款後分數跌破 0 誤觸「擊飛」
        // （RiichiMatchProgressionPolicy 的擊飛條件）而讓對局提早結束，干擾這裡真正要測的
        // 「Ron 後有沒有正確銜接 AdvanceRoundUseCase」。
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        ).copy(score = 25000)
        val respondent = FakeMahjongPlayerFactory.create(id = respondentId, initialSeat = Wind.SOUTH, hand = respondentHand, playerRuleState = RiichiPlayerState())
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, respondent),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(respondentId)),
        )
        return table
    }

    // 役牌發已成立（1 翻）、單騎聽白（欲榮和的那張牌）
    private fun ronReadyHand(): Hand = Hand(
        tiles = listOf(
            Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
            Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3), Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Dot, 5), Tile.Numeric(Tile.Suit.Dot, 6), Tile.Numeric(Tile.Suit.Dot, 7),
            Tile.Numeric(Tile.Suit.Bamboo, 6), Tile.Numeric(Tile.Suit.Bamboo, 7), Tile.Numeric(Tile.Suit.Bamboo, 8),
        ).map { FakeIdentifiedTileFactory.create(it) } + FakeIdentifiedTileFactory.create(Tile.Honor.White),
    )

    /**
     * 驗證 [RespondToDiscardUseCase] 解析為榮和時，`AdvanceRoundUseCase` 會被銜接。
     */
    @Test
    fun `test respond to discard resolving as ron chains advance round`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val table = discardReactionTable(discarderId, respondentId, ronReadyHand())
        fixtures.gameRepo.setTableState(table)
        val whiteTileId = table.pendingReaction!!.tileId

        val result = fixtures.coordinator.dispatchThenDrive(gameId, respondentId, GameCommand.RespondToDiscard(GameAction.Ron(whiteTileId)))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.players.first { it.id == respondentId }.actionHistory.isEmpty())
    }

    /** 一炮多響只交出一次和牌呈現，同時包含兩位贏家，結算排行列出所有玩家並反映兩位贏家的得分。 */
    @Test
    fun `test double ron publishes one win presentation with both winners`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val firstWinnerId = Uuid.random()
        val secondWinnerId = Uuid.random()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        ).copy(score = 25000)
        val winners = listOf(firstWinnerId to Wind.SOUTH, secondWinnerId to Wind.WEST).map { (id, seat) ->
            FakeMahjongPlayerFactory.create(id = id, initialSeat = seat, hand = ronReadyHand(), playerRuleState = RiichiPlayerState()).copy(score = 25000)
        }
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder) + winners,
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(firstWinnerId, secondWinnerId)),
        )
        fixtures.gameRepo.setTableState(table)

        fixtures.coordinator.dispatchThenDrive(gameId, firstWinnerId, GameCommand.RespondToDiscard(GameAction.Ron(discardedTile.id)))
        assertTrue(fixtures.presentationPublisher.getPublishedWinPresentations(gameId).isEmpty(), "The window waits for the other winner.")
        val result = fixtures.coordinator.dispatchThenDrive(gameId, secondWinnerId, GameCommand.RespondToDiscard(GameAction.Ron(discardedTile.id)))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val published = fixtures.presentationPublisher.getPublishedWinPresentations(gameId).single()
        assertEquals(setOf(firstWinnerId, secondWinnerId), published.winnerPlayerIds.toSet())
        val ranking = published.settlement.ranking.players.associateBy { it.playerId }
        assertEquals(setOf(discarderId, firstWinnerId, secondWinnerId), ranking.keys)
        assertTrue(ranking.getValue(firstWinnerId).currentScore > ranking.getValue(firstWinnerId).previousScore)
        assertTrue(ranking.getValue(secondWinnerId).currentScore > ranking.getValue(secondWinnerId).previousScore)
        assertTrue(ranking.getValue(discarderId).currentScore < ranking.getValue(discarderId).previousScore)
    }

    /** 驗證 blocking presentation 期間的真人命令會在進入權威流程前遭拒。 */
    @Test
    fun `test busy presentation rejects player command`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val table = discardReactionTable(discarderId, respondentId, ronReadyHand())
        fixtures.gameRepo.setTableState(table)
        fixtures.presentationBusyGate.setBusy(gameId, true)
        val whiteTileId = table.pendingReaction!!.tileId

        val result = fixtures.coordinator.dispatchThenDrive(gameId, respondentId, GameCommand.RespondToDiscard(GameAction.Ron(whiteTileId)))

        val error = assertIs<Outcome.Error<GameError>>(result).error
        assertEquals(GameError.UnsupportedAction(gameId, respondentId, "mahjongcraft:presentation_busy"), error)
        assertEquals(table, fixtures.gameRepo.getTableState(gameId))
    }

    /**
     * 迴歸測試：模擬 server 在榮和結算與待推進流程已持久化、但呈現尚未結束時重啟；
     * 新 session 可單純從權威桌況補完推進，且重複恢復不會再推進一次。
     */
    @Test
    fun `test persisted ron settlement resumes round transition after restart`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val table = discardReactionTable(discarderId, respondentId, ronReadyHand())
        fixtures.gameRepo.setTableState(table)
        val whiteTileId = table.pendingReaction!!.tileId

        val settlement = fixtures.router(
            gameId,
            respondentId,
            GameCommand.RespondToDiscard(GameAction.Ron(whiteTileId)),
        )
        assertTrue(settlement is Outcome.Success)
        assertTrue(
            fixtures.gameRepo.getTableState(gameId)!!.players
                .first { it.id == respondentId }
                .actionHistory
                .any { it is GameAction.Ron },
        )
        fixtures.gameRepo.updateGame(gameId) { game ->
            val state = game!!.tableState
            val directive = if (state.dealerPlayerId == respondentId) {
                RoundTransitionDirective.REPEAT_DEALER
            } else {
                RoundTransitionDirective.ADVANCE_DEALER
            }
            game.copy(
                pendingTransition = PendingGameTransition.AdvanceRound,
                roundCompletion = RoundCompletionSummary(
                    outcomeId = BuiltInRoundOutcomeIds.RON,
                    classification = RoundCompletionClassification.WIN,
                    beneficiaryPlayerIds = setOf(respondentId),
                    responsiblePlayerIds = setOf(discarderId),
                    transitionDirective = directive,
                    settledScoresByPlayerId = state.players.associate { it.id to it.score },
                ),
            ) to Unit
        }

        assertTrue(fixtures.coordinator.advanceAutomatedPlayerStep(gameId))
        val advancedState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(advancedState.players.first { it.id == respondentId }.actionHistory.isEmpty())
        assertEquals(false, fixtures.coordinator.advanceAutomatedPlayerStep(gameId))
    }

    /**
     * 驗證 [RespondToDiscardUseCase] 解析為碰（未結束本局）時，`AdvanceRoundUseCase` 不會被誤觸發。
     */
    @Test
    fun `test respond to discard resolving as pon does not chain advance round`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val respondentId = Uuid.random()
        val whiteTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val whiteTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val filler = (1..10).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1)) }
        val table = discardReactionTable(discarderId, respondentId, Hand(tiles = listOf(whiteTile1, whiteTile2) + filler))
        fixtures.gameRepo.setTableState(table)
        val whiteTileId = table.pendingReaction!!.tileId

        val ponAction = GameAction.Pon(whiteTileId, listOf(whiteTile1.id, whiteTile2.id))
        val result = fixtures.coordinator.dispatchThenDrive(gameId, respondentId, GameCommand.RespondToDiscard(ponAction))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount)
        assertEquals(ponAction, newState.players.first { it.id == respondentId }.actionHistory.last())
    }

    private fun chankanTable(declarerId: Uuid, robberId: Uuid, reservedWallTiles: List<IdentifiedTile>, robberHand: Hand): TableState {
        val whiteTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val whiteTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val whiteTile3 = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val robbedWhiteTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val existingPon = Meld(MeldType.PON, listOf(whiteTile1, whiteTile2, whiteTile3), sourceTile = whiteTile3, sourceDirection = RelativeDirection.Left)
        val kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, robbedWhiteTile.id, emptyList())
        // score 理由同 discardReactionTable：避免放槍付款後分數跌破 0 誤觸擊飛。
        val declarer = FakeMahjongPlayerFactory.create(
            id = declarerId,
            initialSeat = Wind.EAST,
            hand = Hand(melds = listOf(existingPon), lastDrawn = robbedWhiteTile),
        ).copy(score = 25000)
        val robber = FakeMahjongPlayerFactory.create(id = robberId, initialSeat = Wind.SOUTH, hand = robberHand, playerRuleState = RiichiPlayerState())
        return FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, robber),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            reservedWallTiles = reservedWallTiles,
            currentPlayerIndex = 0,
            pendingRobbingReaction = PendingRobbingReaction(declarerId, kanAction, robbedWhiteTile, setOf(robberId)),
            dynamicRuleState = RiichiDynamicState(),
        )
    }

    /**
     * 驗證 [RespondToRobbingUseCase] 解析為榮和時，`AdvanceRoundUseCase` 會被銜接。
     */
    @Test
    fun `test respond to chankan resolving as ron chains advance round`() = runTest {
        val fixtures = Fixtures()
        val declarerId = Uuid.random()
        val robberId = Uuid.random()
        val table = chankanTable(declarerId, robberId, emptyList(), ronReadyHand())
        fixtures.gameRepo.setTableState(table)
        val robbedTileId = table.pendingRobbingReaction!!.robbedTile.id

        val result = fixtures.coordinator.dispatchThenDrive(gameId, robberId, GameCommand.RespondToRobbing(GameAction.Ron(robbedTileId)))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertTrue(newState.players.first { it.id == robberId }.actionHistory.isEmpty())
    }

    /**
     * 驗證 [RespondToRobbingUseCase] 全員放過、補做套用副露（未結束本局）時，`AdvanceRoundUseCase`
     * 不會被誤觸發。
     */
    @Test
    fun `test respond to chankan resolving as all pass does not chain advance round`() = runTest {
        val fixtures = Fixtures()
        val declarerId = Uuid.random()
        val robberId = Uuid.random()
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val table = chankanTable(declarerId, robberId, listOf(rinshanTile), ronReadyHand())
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, robberId, GameCommand.RespondToRobbing(GameAction.Pass))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount)
        val declarer = newState.players.first { it.id == declarerId }
        assertEquals(MeldType.ADDED_KAN, declarer.hand.melds.single().type, "The all-pass resume should have applied the kan.")
        assertTrue(declarer.actionHistory.isNotEmpty(), "The kan/draw should still be recorded; the hand did not end.")
    }

    // ---- 一般路徑：不該誤觸發 ----

    /**
     * 驗證一般摸牌成功（無牌山摸盡）不會觸發任何銜接。
     */
    @Test
    fun `test ordinary draw does not chain anything`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val drawnTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            tileWall = TileWall(listOf(drawnTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, playerId, GameCommand.Draw)

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount)
        assertEquals(drawnTile, newState.players.first { it.id == playerId }.hand.lastDrawn)
        assertEquals(
            PlayerDecisionPhase.OWN_TURN,
            fixtures.decisionTimerManager.getStatuses(gameId).getValue(playerId).phase,
        )
    }

    /**
     * 驗證一般槓牌成功（無搶槓視窗開啟）不會觸發任何銜接。
     */
    @Test
    fun `test ordinary kan does not chain anything`() = runTest {
        val fixtures = Fixtures()
        val playerId = Uuid.random()
        val east1 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east2 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east3 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val east4 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val rinshanTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val player = FakeMahjongPlayerFactory.create(id = playerId, initialSeat = Wind.EAST, hand = Hand(tiles = listOf(east1, east2, east3), lastDrawn = east4))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            reservedWallTiles = listOf(rinshanTile) +
                List(13) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)) },
            currentPlayerIndex = 0,
            dynamicRuleState = RiichiDynamicState(),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, playerId, GameCommand.Kan(GameAction.KanType.CLOSED_KAN, east4.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(0, newState.comboCount)
        assertEquals(MeldType.CLOSED_KAN, newState.players.first { it.id == playerId }.hand.melds.single().type)
    }

    // ---- 錯誤原樣傳遞 ----

    /**
     * 驗證非 `WallExhausted` 的錯誤原樣回傳，不觸發任何銜接呼叫，桌況完全不變。
     */
    @Test
    fun `test non wall exhausted errors pass through untouched`() = runTest {
        val fixtures = Fixtures()
        val currentPlayerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val currentPlayer = FakeMahjongPlayerFactory.create(id = currentPlayerId, initialSeat = Wind.EAST)
        val other = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(currentPlayer, other), config = RiichiRuleConfig(gameLength = RiichiGameLength.East), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.coordinator.dispatchThenDrive(gameId, otherPlayerId, GameCommand.Draw)

        assertTrue(result is Outcome.Error)
        assertEquals(GameError.NotPlayersTurn(otherPlayerId, gameId), result.error)
        assertEquals(table, fixtures.gameRepo.getTableState(gameId))
    }

    /** 驗證失敗命令只校正決策狀態，不會重新開始目前玩家已存在的基本思考時間。 */
    @Test
    fun `test failed command does not reset active decision timer`() = runTest {
        val fixtures = Fixtures()
        val currentPlayerId = Uuid.random()
        val otherPlayerId = Uuid.random()
        val drawnTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val currentPlayer = FakeMahjongPlayerFactory.create(
            id = currentPlayerId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = drawnTile),
        )
        val otherPlayer = FakeMahjongPlayerFactory.create(id = otherPlayerId, initialSeat = Wind.SOUTH)
        fixtures.gameRepo.setTableState(
            FakeTableStateFactory.create(
                id = gameId,
                players = listOf(currentPlayer, otherPlayer),
                config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            ),
        )
        fixtures.decisionTimerManager.reconcile(gameId)
        fixtures.clock.nowMillis = 2_000L

        val result = fixtures.coordinator.dispatchThenDrive(gameId, otherPlayerId, GameCommand.Draw)

        assertTrue(result is Outcome.Error)
        assertEquals(
            3_000L,
            fixtures.decisionTimerManager.getStatuses(gameId).getValue(currentPlayerId).time.baseRemainingMillis,
        )
    }

    // ---- AI 自動出手 ----

    /**
     * 驗證人類捨牌後、輪到的下一位是 AI 且無人可反應時：同一次 `dispatchThenDrive(...)` 呼叫內，AI
     * 已經自動摸牌並捨牌，回合正確推回人類——不需要呼叫端再送出任何命令。
     */
    @Test
    fun `test ai automatically draws and discards after human discard advances turn to it`() = runTest {
        val fixtures = Fixtures()
        val humanId = Uuid.random()
        val aiId = Uuid.random()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val human = FakeMahjongPlayerFactory.create(id = humanId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = discardedTile))
        // 全是條子，跟人類打出的餅牌無關，確保不會意外開啟反應視窗
        val aiHandTiles = (1..13).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, ((it - 1) % 9) + 1)) }
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = aiHandTiles),
            playerRuleState = RiichiPlayerState(),
        )
        val drawnTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(human, ai),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            tileWall = TileWall(listOf(drawnTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table, mapOf(aiId to RandomAiStrategy.KEY))

        val result = fixtures.coordinator.dispatchThenDrive(gameId, humanId, GameCommand.Discard(discardedTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        val updatedAi = newState.players.first { it.id == aiId }
        assertEquals(1, updatedAi.discardPile.entries.size, "The AI should have automatically drawn and discarded.")
        assertEquals(0, newState.currentPlayerIndex, "Turn should have advanced back to the human.")
    }

    /**
     * 驗證人類捨牌開啟反應視窗、視窗裡唯一有資格者是 AI 時：同一次呼叫內 AI 自動回應，視窗正確
     * 關閉，不需要呼叫端再送出任何命令。
     */
    @Test
    fun `test ai automatically resolves a reaction window it is the sole eligible responder for`() = runTest {
        val fixtures = Fixtures()
        val humanId = Uuid.random()
        val aiId = Uuid.random()
        val southTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val human = FakeMahjongPlayerFactory.create(id = humanId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = southTile))
        val southTile1 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val southTile2 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val filler = (1..10).map { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, (it % 9) + 1)) }
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(southTile1, southTile2) + filler),
            playerRuleState = RiichiPlayerState(),
        )
        // 若 AI 選擇過牌（而非碰），輪到的下一位就是它自己、需要先摸牌——牌山至少要有 1 張牌，
        // 否則會撞上牌山摸盡 → 一般流局 → 連莊/過莊判定，讓這個測試意外變成在測完全不同的情境。
        val nextDrawTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9))
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(human, ai),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.East),
            tileWall = TileWall(listOf(nextDrawTile)),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table, mapOf(aiId to RandomAiStrategy.KEY))

        val result = fixtures.coordinator.dispatchThenDrive(gameId, humanId, GameCommand.Discard(southTile.id))

        assertTrue(result is Outcome.Success, "Expected Success but got $result")
        val newState = fixtures.gameRepo.getTableState(gameId)!!
        assertEquals(null, newState.pendingReaction, "The AI should have automatically resolved the reaction window.")
    }

    /**
     * 驗證整場對局已經結束（下一次過莊判定就會讓 isMatchOver 成立）、且當前玩家恰好是 AI 且尚未
     * 摸牌時，`driveAutomatedPlayers` 能偵測到桌況沒有任何進展（摸牌因牌山已空觸發 WallExhausted →
     * 流局銜接 → 推進嘗試因 isMatchOver 而維持桌況不變）並提前跳出迴圈，而不是跑滿 100 次的迭代
     * 上限——改用「偵測沒有進展」取代單純固定次數上限，取代過去（僅靠 100 次上限）的做法。
     */
    @Test
    fun `test driveAutomatedPlayers stops early when table state makes no progress`() = runTest {
        val fixtures = Fixtures()
        val aiId = Uuid.random()
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.EAST,
            playerRuleState = RiichiPlayerState(),
        )
        val other = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        // 一局制（OneGame，totalRounds = 1）且已經是 roundNumber = 1，下一次過莊判定就會讓
        // isMatchOver 成立；牌山已空，AI 輪到自己回合但尚未摸牌，會不斷被判斷「該幫它摸牌」。
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(ai, other),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.OneGame),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
            roundNumber = 1,
        )
        fixtures.gameRepo.setTableState(table, mapOf(aiId to RandomAiStrategy.KEY))

        fixtures.coordinator.driveAutomatedPlayers(gameId)

        assertTrue(
            fixtures.gameRepo.getTableStateCallCount < 20,
            "Should stop after detecting no progress on the first iteration, not loop anywhere near the " +
                "100-iteration cap (actual call count: ${fixtures.gameRepo.getTableStateCallCount}).",
        )
    }

    /**
     * 迴歸測試：驗證對局結束後（[Game.isMatchOver] 成立）再次呼叫 [GameFlowCoordinator.driveAutomatedPlayers]
     * 不會重複觸發流局結算——[Game.isMatchOver] 加入前，`AiTurnDriver`／`ForcedAutoPlayDriver` 不知道
     * 對局已經結束，會不斷嘗試對已空的牌山摸牌、不斷重新觸發 `DeclareExhaustiveDrawUseCase`，導致
     * 流局點數被重複套用；這支測試模擬「心跳每個 tick 都呼叫一次」的情境，驗證第二次呼叫之後分數
     * 不會再變動。
     */
    @Test
    fun `test driveAutomatedPlayers does not re-apply exhaustive draw settlement after match is over`() = runTest {
        val fixtures = Fixtures()
        val aiId = Uuid.random()
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.EAST,
            playerRuleState = RiichiPlayerState(),
        )
        val other = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH, playerRuleState = RiichiPlayerState())
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(ai, other),
            config = RiichiRuleConfig(gameLength = RiichiGameLength.OneGame),
            tileWall = TileWall(emptyList()),
            currentPlayerIndex = 0,
            roundNumber = 1,
        )
        fixtures.gameRepo.setTableState(table, mapOf(aiId to RandomAiStrategy.KEY))

        // 第一次呼叫：觸發 WallExhausted → 流局結算 → isMatchOver 成立。
        fixtures.coordinator.driveAutomatedPlayers(gameId)
        val scoresAfterFirstCall = fixtures.gameRepo.getTableState(gameId)!!.players.associate { it.id to it.score }
        assertTrue(fixtures.gameRepo.getGame(gameId)!!.isMatchOver, "Match should be over after the wall is exhausted in a one-round match.")

        // 模擬心跳每個 tick 都再呼叫一次：分數不該再變動。
        repeat(5) { fixtures.coordinator.driveAutomatedPlayers(gameId) }
        val scoresAfterMoreCalls = fixtures.gameRepo.getTableState(gameId)!!.players.associate { it.id to it.score }

        assertEquals(scoresAfterFirstCall, scoresAfterMoreCalls, "Scores must not change after the match has already ended.")
    }

    // ---- AI 決策的條件式提交 ----

    /**
     * 驗證 AI 決策期間權威遊戲被改變時，這次決策完全不套用，下一輪重新判斷並決策後才捨牌。
     */
    @Test
    fun `test an ai decision made on a changed game is discarded and decided again`() = runTest {
        val strategy = ChangingGameStrategy(changesGameOnFirstCall = true)
        val fixtures = Fixtures(extraStrategies = mapOf(CHANGING_STRATEGY_KEY to strategy))
        strategy.gameRepo = fixtures.gameRepo
        val aiId = Uuid.random()
        val drawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = drawn),
            playerRuleState = RiichiPlayerState(),
        )
        val human = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(ai, human),
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)))),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table, mapOf(aiId to CHANGING_STRATEGY_KEY))

        fixtures.coordinator.driveAutomatedPlayers(gameId)

        assertEquals(2, strategy.gameCommandCalls, "The stale decision should be made again on the changed game.")
        val updatedAi = fixtures.gameRepo.getTableState(gameId)!!.players.first { it.id == aiId }
        assertEquals(listOf(drawn.id), updatedAi.discardPile.entries.map { it.tile.id }, "Only the second decision should be applied.")
    }

    /**
     * 驗證 AI 的開局準備提交期間權威遊戲被改變時，這次提交完全不套用，下一輪重新決定後才完成準備步驟。
     */
    @Test
    fun `test an ai round preparation submission on a changed game is discarded and decided again`() = runTest {
        val strategy = ChangingGameStrategy(changesGameOnFirstCall = true)
        val resolvers = RoundPreparationResolverRegistry().apply { register(ConfirmationResolver) }
        val fixtures = Fixtures(extraStrategies = mapOf(CHANGING_STRATEGY_KEY to strategy), roundPreparationResolvers = resolvers)
        strategy.gameRepo = fixtures.gameRepo
        val humanId = Uuid.random()
        val aiId = Uuid.random()
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(
                FakeMahjongPlayerFactory.create(id = humanId, initialSeat = Wind.EAST),
                FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.SOUTH),
            ),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setGame(
            Game(
                tableState = table,
                flowConfig = GameFlowConfig(),
                aiPlayerStrategyKeys = mapOf(aiId to CHANGING_STRATEGY_KEY),
                pendingRoundPreparation = PendingRoundPreparation(
                    stepId = "test:confirm",
                    stepIndex = 0,
                    inputSpecsByPlayerId = mapOf(aiId to RoundPreparationInputSpec.Confirmation),
                ),
            ),
        )

        fixtures.coordinator.driveAutomatedPlayers(gameId)

        assertEquals(2, strategy.preparationCalls, "The stale submission should be decided again on the changed game.")
        assertNull(fixtures.gameRepo.getGame(gameId)?.pendingRoundPreparation)
    }

    /**
     * 驗證 AI 送出的擴充命令寫入同一局兩次、即使 handler 攔下例外並回報成功，這次推進仍以錯誤停止：第一次寫入維持
     * 提交、不重試，也不接著做系統銜接。
     */
    @Test
    fun `test an ai command writing the game twice stops the advance`() = runTest {
        val strategy = FixedCommandStrategy(GameCommand.Extension(DoubleWriteCommand))
        val fixtures = Fixtures(
            extraStrategies = mapOf(FIXED_STRATEGY_KEY to strategy),
            extraCommandHandlers = { registry ->
                registry.register(DoubleWriteCommand::class) { context ->
                    object : ExtensionGameCommandHandler<DoubleWriteCommand> {
                        override suspend fun execute(gameId: Uuid, playerId: Uuid, command: DoubleWriteCommand): Outcome<Unit, GameError> {
                            context.gameRepository.updateGame(gameId) { it?.copy(automaticControlRevision = it.automaticControlRevision + 1) to Unit }
                            runCatching { context.gameRepository.updateGame(gameId) { it?.copy(isMatchOver = true) to Unit } }
                            return Outcome.Success(Unit)
                        }
                    }
                }
            },
        )
        val aiId = Uuid.random()
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(ai, FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setTableState(table, mapOf(aiId to FIXED_STRATEGY_KEY))

        val error = assertFailsWith<IllegalStateException> { fixtures.coordinator.driveAutomatedPlayers(gameId) }

        assertIs<ExpectedGameWrittenTwiceException>(error.cause)
        assertEquals(1, strategy.calls, "A contract violation must not be decided again.")
        val game = assertNotNull(fixtures.gameRepo.getGame(gameId))
        assertEquals(1L, game.automaticControlRevision, "The first write stays committed.")
        assertEquals(false, game.isMatchOver)
    }

    /**
     * 驗證 AI 的開局準備策略沒有在等待上限內給出結果時，改用解析器的可重現提交並完成準備步驟。
     */
    @Test
    fun `test an ai round preparation past the timeout submits the fallback`() = runTest {
        val resolvers = RoundPreparationResolverRegistry().apply { register(ConfirmationResolver) }
        val fixtures = Fixtures(extraStrategies = mapOf(HANGING_STRATEGY_KEY to HangingStrategy), roundPreparationResolvers = resolvers)
        val humanId = Uuid.random()
        val aiId = Uuid.random()
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(
                FakeMahjongPlayerFactory.create(id = humanId, initialSeat = Wind.EAST),
                FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.SOUTH),
            ),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setGame(
            Game(
                tableState = table,
                flowConfig = GameFlowConfig(),
                aiPlayerStrategyKeys = mapOf(aiId to HANGING_STRATEGY_KEY),
                pendingRoundPreparation = PendingRoundPreparation(
                    stepId = "test:confirm",
                    stepIndex = 0,
                    inputSpecsByPlayerId = mapOf(aiId to RoundPreparationInputSpec.Confirmation),
                ),
            ),
        )

        fixtures.coordinator.driveAutomatedPlayers(gameId)

        assertNull(fixtures.gameRepo.getGame(gameId)?.pendingRoundPreparation)
        assertEquals(AiDecisionExecutor.DEFAULT_TIMEOUT.inWholeMilliseconds, testScheduler.currentTime)
    }

    /** 永遠不會給出結果、只在被取消時結束的 AI 策略。 */
    private object HangingStrategy : MahjongAiStrategy {
        override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand = awaitCancellation()

        override suspend fun decideRoundPreparation(context: RoundPreparationAiContext): RoundPreparationSubmission = awaitCancellation()
    }

    /** 每次都回傳同一個命令的 AI 策略。 */
    private class FixedCommandStrategy(private val command: GameCommand) : MahjongAiStrategy {
        /** 被呼叫的次數。 */
        var calls = 0
            private set

        override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand {
            calls++
            return command
        }
    }

    /** 對同一局寫入兩次的測試用擴充命令。 */
    private data object DoubleWriteCommand : ExtensionGameCommand

    /**
     * 決策時可選擇先改變權威遊戲一次的 AI 策略，用來模擬 AI 思考期間其他入口修改了同一局。
     *
     * @property changesGameOnFirstCall 第一次被呼叫時是否改變權威遊戲。
     */
    private class ChangingGameStrategy(private val changesGameOnFirstCall: Boolean) : MahjongAiStrategy {
        /** 決策期間要改變的權威倉庫；建立 [Fixtures] 後設定。 */
        lateinit var gameRepo: FakeGameRepository

        /** [decideGameCommand] 的呼叫次數。 */
        var gameCommandCalls = 0
            private set

        /** [decideRoundPreparation] 的呼叫次數。 */
        var preparationCalls = 0
            private set

        override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand {
            gameCommandCalls++
            if (gameCommandCalls == 1) changeGame(context.snapshot.id)
            val tile = context.snapshot.players.first { it.id == context.selfId }.hand.lastDrawn
            return GameCommand.Discard(checkNotNull(tile).id)
        }

        override suspend fun decideRoundPreparation(context: RoundPreparationAiContext): RoundPreparationSubmission {
            preparationCalls++
            if (preparationCalls == 1) changeGame(context.snapshot.id)
            return RoundPreparationSubmission.Confirmed
        }

        private suspend fun changeGame(gameId: Uuid) {
            if (!changesGameOnFirstCall) return
            gameRepo.updateGame(gameId) { game -> game?.copy(automaticControlRevision = game.automaticControlRevision + 1) to Unit }
        }
    }

    /** 收齊確認後不改桌況、也沒有下一步的開局準備解析器。 */
    private object ConfirmationResolver : RoundPreparationResolver {
        override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

        override fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>): PendingRoundPreparation? = null

        override fun resolve(
            tableState: TableState,
            preparation: PendingRoundPreparation,
            ruleModule: MahjongRuleModule<*>,
        ): RoundPreparationResolution = RoundPreparationResolution(tableState, nextStep = null)
    }

    private companion object {
        /** [ChangingGameStrategy] 的策略 key。 */
        const val CHANGING_STRATEGY_KEY = "test:changing_game"

        /** [FixedCommandStrategy] 的策略 key。 */
        const val FIXED_STRATEGY_KEY = "test:fixed_command"

        /** [HangingStrategy] 的策略 key。 */
        const val HANGING_STRATEGY_KEY = "test:hanging"
    }
}

/** coordinator 計時整合測試使用的可控單調時間來源。 */
private class MutableMonotonicClock : MonotonicClock {
    /** 目前回傳的單調時間毫秒數。 */
    var nowMillis: Long = 0L

    /** 回傳測試指定的單調時間。 */
    override fun nowMillis(): Long = nowMillis
}
