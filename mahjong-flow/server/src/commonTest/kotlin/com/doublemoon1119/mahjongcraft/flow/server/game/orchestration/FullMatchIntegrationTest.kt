package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.ExhaustiveDrawSettlementPresentationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAbortiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareSuukanNagareUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolvePostReactionRoundOutcomeUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolveWinRoundContinuationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ReturnToRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.time.MonotonicClockImpl
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 整場對局的端對端整合測試：4 位 AI 玩家從開局一路打到整場對局結束（東風戰，4 局 + 可能的連莊），
 * 完全透過 [GameFlowCoordinator] 驅動，不經過任何 Minecraft 平台層。
 *
 * 目的是驗證編排層本身（[GameFlowCoordinator]/[AiTurnDriver]/連莊過莊自動銜接）撐得住一整場對局，
 * 不會卡住、分數守恆——這是目前完全空缺的一層測試：既有測試都只針對單一 use case 的窄範圍驗證，
 * 從沒有一個測試證明整條編排鏈路真的能把一整場牌局打完。`driveAutomatedPlayers` 先前的無限迴圈 bug
 * 正是在接近這種整合情境時才被發現的。
 *
 * 刻意用 [FakeAiStrategy]（見下方）而非 [RandomAiStrategy]：
 * 這個測試關心的是「編排層撐不撐得住」，不是「AI 選得好不好」，用固定策略讓行為可預期、測試結果
 * 穩定重現，不需要處理隨機性帶來的不穩定。
 */
class FullMatchIntegrationTest {

    private class Fixtures {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val snapshotRepo = FakeGameSnapshotRepository()
        val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepo, snapshotRepo, GameVisibilityPolicyImpl())
        val handSortPreferenceStore = HandSortPreferenceStore()
        val eventPublisher = FakeGameEventPublisher()
        val presentationPublisher = FakeGamePresentationPublisher()
        val presentationBusyGate = FakeGamePresentationBusyGate()
        val declareRiichiUseCase = DeclareRiichiUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, handSortPreferenceStore, eventPublisher, presentationPublisher)
        val extensionCommandRegistry = ExtensionGameCommandExecutorRegistry().apply {
            registerRiichiGameCommandHandler(declareRiichiUseCase)
        }
        val router = GameActionRouter(
            drawTileUseCase = DrawTileUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                eventPublisher,
                presentationPublisher,
            ),
            discardTileUseCase = DiscardTileUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                eventPublisher,
                presentationPublisher,
            ),
            declareTsumoUseCase = DeclareTsumoUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
            declareKanUseCase = DeclareKanUseCase(gameRepo, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
            respondToDiscardUseCase = RespondToDiscardUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                eventPublisher,
                presentationPublisher,
            ),
            respondToKanUseCase = RespondToKanUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                eventPublisher,
                presentationPublisher,
            ),
            declareAbortiveDrawUseCase = DeclareAbortiveDrawUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                eventPublisher,
            ),
            extensionCommandRegistry = extensionCommandRegistry,
        )
        val getLegalActionsUseCase = GetLegalActionsUseCase(gameRepo, moduleRegistry)
        val aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = FakeAiStrategy.KEY).apply {
            register(FakeAiStrategy.KEY) { FakeAiStrategy() }
        }
        val aiTurnDriver =
            AiTurnDriver(gameRepo, getLegalActionsUseCase, aiStrategyRegistry, GameVisibilityPolicyImpl())
        val clock = MonotonicClockImpl()
        val decisionTimerManager = GameDecisionTimerManager(
            gameRepository = gameRepo,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(clock),
            clock = clock,
        )
        val coordinator = GameFlowCoordinator(
            gameActionRouter = router,
            extensionCommandRegistry = extensionCommandRegistry,
            gameRepository = gameRepo,
            moduleRegistry = moduleRegistry,
            declareExhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                eventPublisher,
            ),
            resolvePostReactionRoundOutcomeUseCase = ResolvePostReactionRoundOutcomeUseCase(
                gameRepo,
                moduleRegistry,
                PostReactionRoundOutcomeResolverRegistry().apply { freeze() },
                snapshotSynchronizer,
            ),
            resolveWinRoundContinuationUseCase = ResolveWinRoundContinuationUseCase(
                gameRepo,
                moduleRegistry,
                WinRoundContinuationResolverRegistry().apply { freeze() },
                snapshotSynchronizer,
            ),
            declareSuukanNagareUseCase = DeclareSuukanNagareUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                eventPublisher,
            ),
            advanceRoundUseCase = AdvanceRoundUseCase(
                gameRepo,
                moduleRegistry,
                snapshotSynchronizer,
                handSortPreferenceStore,
                eventPublisher,
                presentationPublisher,
            ),
            // FakeGameRepository 獨立於 AuthoritativeStateStore，這裡的 ReturnToRoomUseCase 接不到
            // 同一份對局資料，只是滿足建構子的無害 no-op——本測試關心的是編排層撐不撐得住整場對局，
            // 不是 Game → Room 轉移本身。
            returnToRoomUseCase = ReturnToRoomUseCase(
                AuthoritativeStateStore(),
                FakeRoomSnapshotRepository(),
                FakeRoomEventPublisher(),
                presentationPublisher,
            ),
            aiTurnDriver = aiTurnDriver,
            forcedAutoPlayDriver = ForcedAutoPlayDriver(gameRepo),
            decisionTimerManager = decisionTimerManager,
            decisionTimerSynchronizationService = DecisionTimerSynchronizationService(
                decisionTimerManager,
                gameRepo,
                FakeDecisionTimerUpdatePublisher(),
            ),
            presentationBusyGate = presentationBusyGate,
            exhaustiveDrawSettlementPresentationService = ExhaustiveDrawSettlementPresentationService(presentationPublisher),
            presentationPublisher = presentationPublisher,
        )
    }

    /**
     * 驗證 4 位 AI 玩家能把整場東風戰（4 局，含可能的連莊）打完，不會卡住、分數守恆。
     *
     * 只呼叫一次 [GameFlowCoordinator.driveAutomatedPlayers]——它內部會自動開好幾批次直到真的收斂
     * 為止（見其 KDoc），不需要呼叫端自己重複呼叫湊迭代預算；若真的卡在某處跑不完，這裡會直接拋出
     * `IllegalStateException`，測試會直接失敗並帶出清楚的錯誤訊息，不需要額外再比較前後桌況。
     */
    @Test
    fun `test four ai players play a full east-only match to completion`() = runTest {
        val fixtures = Fixtures()
        val gameId = Uuid.random()
        val playerIds = List(4) { Uuid.random() }
        val config = RiichiRuleConfig(gameLength = RiichiGameLength.East)
        val module = fixtures.moduleRegistry.getModule(config)
        val initialState = GameInitializer.initialize(
            id = gameId,
            playerIds = playerIds,
            module = module,
            aiPlayerStrategyKeys = playerIds.associateWith { FakeAiStrategy.KEY },
        ).tableState
        fixtures.gameRepo.setTableState(initialState)

        fixtures.coordinator.driveAutomatedPlayers(gameId)
        val finalState = fixtures.gameRepo.getTableState(gameId)

        assertTrue(
            finalState!!.roundNumber >= RiichiGameLength.East.totalRounds,
            "The match should have progressed through all ${RiichiGameLength.East.totalRounds} rounds, " +
                "not stalled early (actual roundNumber: ${finalState.roundNumber}).",
        )

        val totalScore = finalState.players.sumOf { it.score }
        assertEquals(
            4 * config.scoreConfig.initialScore,
            totalScore,
            "Points only move between players (nobody declares riichi in this test, so no sticks leave the " +
                "table either); the total should be conserved across the whole match.",
        )
    }

    /**
     * 供整場對局整合測試使用的固定策略：刻意不主動鳴牌（吃/碰/槓/立直/九種九牌），只在自然出現
     * 榮和/自摸機會時才拿，其餘時候單純摸牌後打出剛摸到的牌——這個測試關心的是編排層撐不撐得住
     * 一整場對局，不是驗證每種行牌路徑，維持策略單純、行為可預期比覆蓋率更重要（各種鳴牌/立直/
     * 搶槓路徑已經有各自獨立的單元測試涵蓋）。
     */
    private class FakeAiStrategy : MahjongAiStrategy {
        companion object {
            const val KEY = "fake-deterministic"
        }

        override suspend fun decide(context: AiDecisionContext): GameCommand = when (context.phase) {
            AiDecisionPhase.RespondingToDiscard ->
                GameCommand.RespondToDiscard(
                    context.legalActions.firstOrNull { it is GameAction.Ron }
                        ?: GameAction.Pass,
                )

            AiDecisionPhase.RespondingToKan ->
                GameCommand.RespondToKan(
                    context.legalActions.firstOrNull { it is GameAction.Ron }
                        ?: GameAction.Pass,
                )

            AiDecisionPhase.OwnTurn -> {
                if (context.legalActions.contains(GameAction.Tsumo)) {
                    GameCommand.Tsumo
                } else {
                    val hand = context.snapshot.players.first { it.id == context.selfId }.hand
                    val tileId = hand.lastDrawn?.id ?: hand.standingTiles.first().id
                    GameCommand.Discard(tileId)
                }
            }
        }
    }
}
