package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.riichi.registerRiichiGameActionHandler
import com.doublemoon1119.mahjongcraft.ai.riichi.registerRiichiOpponentModel
import com.doublemoon1119.mahjongcraft.flow.common.di.createBuiltInWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiTurnDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ForcedAutoPlayDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameActionRouter
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiGameCommandHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiNagashiManganOutcomeResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiPostActionExhaustiveDrawResolvers
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.registerRiichiWinSettlementDetailResolver
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
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ReturnToRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.time.MonotonicClockImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher

/**
 * 以正式規則模組與遊戲流程組裝、不經過任何平台層的對局環境，供整場對局測試與 AI 對局模擬共用。
 *
 * 內建規則模組、日麻的立直命令、立直 AI handler、流局滿貫與途中流局判定、胡牌詳情皆已登記；AI 策略 registry
 * 建立時是空的，由呼叫端登記要使用的策略。呈現相關的 publisher 使用測試替身，呈現閘門永遠閒置，因此
 * [GameFlowCoordinator.driveAutomatedPlayers] 會一路推進到需要真人操作或整場結束為止。
 *
 * [ReturnToRoomUseCase] 接的是獨立的權威狀態，整場結束後的 Game → Room 轉移不會生效，只停在
 * `PendingGameTransition.ReturnToRoom`。
 *
 * @param defaultStrategyKey AI 策略 registry 遇到未知 key 時的退回 key。
 */
internal class SimulationRuntime(defaultStrategyKey: String) {
    /** 登記內建規則的模組 registry。 */
    val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }

    /** 記錄每一局開局與最終桌況的對局倉庫。 */
    val gameRepository = RoundRecordingGameRepository()

    /** 登記日麻立直 handler 的擴充動作 AI registry。 */
    val extensionActionRegistry = ExtensionGameActionAiRegistry(moduleRegistry).apply { registerRiichiGameActionHandler() }

    /** 登記日麻對手模型的 registry。 */
    val opponentModelRegistry = OpponentModelRegistry().apply { registerRiichiOpponentModel() }

    /** 由呼叫端登記策略的 AI 策略 registry。 */
    val aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = defaultStrategyKey)

    /** 捨牌後的途中流局判定。 */
    private val postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply {
        registerRiichiPostActionExhaustiveDrawResolvers()
        freeze()
    }

    /** 胡牌詳情。 */
    private val winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry().apply {
        registerRiichiWinSettlementDetailResolver()
        freeze()
    }

    /** 最終捨牌後的特殊結果，例如流局滿貫。 */
    private val postReactionRoundOutcomeResolverRegistry = PostReactionRoundOutcomeResolverRegistry().apply {
        registerRiichiNagashiManganOutcomeResolver()
        freeze()
    }

    /** 快照同步；快照只寫入測試替身。 */
    private val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl())

    /** 手牌排序偏好。 */
    private val handSortPreferenceStore = HandSortPreferenceStore()

    /** 遊戲事件的測試替身。 */
    private val eventPublisher = FakeGameEventPublisher()

    /** 呈現請求的測試替身。 */
    private val presentationPublisher = FakeGamePresentationPublisher()

    /** 胡牌呈現的交接。 */
    private val winPresentationHandoff = WinPresentationHandoff()

    /** 永遠閒置的呈現閘門。 */
    private val presentationBusyGate = FakeGamePresentationBusyGate()

    /** 以這個 runtime 的流程服務執行日麻立直命令。 */
    private val extensionCommandExecutor = ExtensionGameCommandExecutor(
        registry = ExtensionGameCommandExecutorRegistry().apply {
            registerRiichiGameCommandHandler()
            freeze()
        },
        context = ExtensionGameCommandContext(
            gameRepository = gameRepository,
            moduleRegistry = moduleRegistry,
            snapshotSynchronizer = snapshotSynchronizer,
            handSortPreferenceStore = handSortPreferenceStore,
            postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolverRegistry,
            eventPublisher = eventPublisher,
            presentationPublisher = presentationPublisher,
        ),
    )

    /** 把命令分派給各 use case。 */
    private val router = GameActionRouter(
        drawTileUseCase = DrawTileUseCase(gameRepository, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
        discardTileUseCase = DiscardTileUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            handSortPreferenceStore,
            postActionExhaustiveDrawResolverRegistry,
            eventPublisher,
            presentationPublisher,
        ),
        declareTsumoUseCase = DeclareTsumoUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            eventPublisher,
            presentationPublisher,
            winPresentationHandoff,
            winCelebrationCueResolverRegistry = createBuiltInWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
        ),
        declareKanUseCase = DeclareKanUseCase(gameRepository, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher),
        respondToDiscardUseCase = RespondToDiscardUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            handSortPreferenceStore,
            eventPublisher,
            presentationPublisher,
            winPresentationHandoff,
            winCelebrationCueResolverRegistry = createBuiltInWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
            postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolverRegistry,
        ),
        respondToKanUseCase = RespondToKanUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            eventPublisher,
            presentationPublisher,
            winPresentationHandoff,
            winCelebrationCueResolverRegistry = createBuiltInWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
        ),
        declareAbortiveDrawUseCase = DeclareAbortiveDrawUseCase(gameRepository, moduleRegistry, snapshotSynchronizer, eventPublisher),
        extensionCommandExecutor = extensionCommandExecutor,
    )

    /** 決策計時使用的時鐘。 */
    private val clock = MonotonicClockImpl()

    /** 決策計時器。 */
    private val decisionTimerManager = GameDecisionTimerManager(
        gameRepository = gameRepository,
        authorityResolver = GameDecisionAuthorityResolver(),
        timerFactory = PlayerDecisionTimerFactory(clock),
        clock = clock,
    )

    /** 驅動 AI 與系統銜接的遊戲流程協調器。 */
    val coordinator = GameFlowCoordinator(
        gameActionRouter = router,
        gameRepository = gameRepository,
        moduleRegistry = moduleRegistry,
        winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
        declareExhaustiveDrawUseCase = DeclareExhaustiveDrawUseCase(gameRepository, moduleRegistry, snapshotSynchronizer, eventPublisher),
        resolvePostReactionRoundOutcomeUseCase = ResolvePostReactionRoundOutcomeUseCase(
            gameRepository,
            moduleRegistry,
            postReactionRoundOutcomeResolverRegistry,
            snapshotSynchronizer,
            winSettlementDetailResolverRegistry,
        ),
        resolveWinRoundContinuationUseCase = ResolveWinRoundContinuationUseCase(
            gameRepository,
            moduleRegistry,
            WinRoundContinuationResolverRegistry().apply { freeze() },
            snapshotSynchronizer,
        ),
        advanceRoundUseCase = AdvanceRoundUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            handSortPreferenceStore,
            eventPublisher,
            presentationPublisher,
        ),
        returnToRoomUseCase = ReturnToRoomUseCase(
            AuthoritativeStateStore(),
            FakeRoomSnapshotRepository(),
            FakeRoomEventPublisher(),
            presentationPublisher,
        ),
        aiTurnDriver = AiTurnDriver(
            gameRepository,
            GetLegalActionsUseCase(gameRepository, moduleRegistry),
            aiStrategyRegistry,
            GameVisibilityPolicyImpl(),
            moduleRegistry,
        ),
        forcedAutoPlayDriver = ForcedAutoPlayDriver(gameRepository),
        decisionAvailabilityService = GameDecisionAvailabilityService(
            presentationBusyGate,
            decisionTimerManager,
            DecisionTimerSynchronizationService(decisionTimerManager, gameRepository, FakeDecisionTimerUpdatePublisher()),
        ),
        presentationBusyGate = presentationBusyGate,
        exhaustiveDrawSettlementPresentationService = ExhaustiveDrawSettlementPresentationService(presentationPublisher),
        winPresentationHandoff = winPresentationHandoff,
        presentationPublisher = presentationPublisher,
    )
}
