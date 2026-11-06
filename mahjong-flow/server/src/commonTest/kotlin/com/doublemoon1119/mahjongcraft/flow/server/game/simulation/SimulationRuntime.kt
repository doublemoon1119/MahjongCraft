package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.bundled.BundledRiichiExtension
import com.doublemoon1119.mahjongcraft.bundled.BundledTaiwanExtension
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiTurnDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomaticGameCommandResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ForcedAutoPlayDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameActionRouter
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionAiDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.ExhaustiveDrawSettlementPresentationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.service.RuleDecisionWinPresentationStager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceAutomaticRuleDecisionUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.BeginRuleDecisionUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAbortiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAndDiscardUseCase
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
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SubmitRuleDecisionUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.time.MonotonicClockImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.repository.FakeRoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * 以正式規則模組與遊戲流程組裝、不經過任何平台層的對局環境，供整場對局測試與 AI 對局模擬共用。
 *
 * 內建規則模組、日麻的立直命令、立直 AI handler、流局滿貫與途中流局判定、胡牌詳情皆已登記；AI 策略 registry
 * 建立時是空的，由呼叫端登記要使用的策略。呈現相關的 publisher 使用測試替身，呈現閘門永遠閒置，因此
 * [driveLikeServer] 會一路推進到需要真人操作或整場結束為止。
 *
 * [ReturnToRoomUseCase] 接的是獨立的權威狀態，整場結束後的 Game → Room 轉移不會生效，只停在
 * `PendingGameTransition.ReturnToRoom`。
 *
 * @param defaultStrategyKey AI 策略 registry 遇到未知 key 時的退回 key。
 */
internal class SimulationRuntime(defaultStrategyKey: String) {
    /** 登記內建規則的模組 registry。 */
    val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }

    /** 記錄每一局開局與最終桌況的對局倉庫。 */
    val gameRepository = RoundRecordingGameRepository()

    /** 登記日麻立直 handler 的擴充動作 AI registry。 */
    val extensionActionRegistry = ExtensionGameActionAiRegistry(moduleRegistry).apply {
        BundledRiichiExtension.registerGameActionAiHandlers(this)
        BundledTaiwanExtension.registerGameActionAiHandlers(this)
    }

    /** 登記日麻對手模型的 registry。 */
    val opponentModelRegistry = OpponentModelRegistry().apply { BundledRiichiExtension.registerOpponentModels(this) }

    /** 由呼叫端登記策略的 AI 策略 registry。 */
    val aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = defaultStrategyKey)

    /** 最近一次中止對局的原因與診斷；沒有中止時為 null。 */
    var abortDiagnostic: String? = null
        private set

    /** 捨牌後的途中流局判定。 */
    private val postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply {
        BundledRiichiExtension.registerPostActionExhaustiveDrawResolvers(this)
        BundledTaiwanExtension.registerPostActionExhaustiveDrawResolvers(this)
        freeze()
    }

    /** 胡牌詳情。 */
    private val winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry(bundledCoreExtensionRegistries().componentRegistry).apply {
        BundledRiichiExtension.registerWinSettlementDetailResolvers(this)
        BundledTaiwanExtension.registerWinSettlementDetailResolvers(this)
        freeze()
    }

    /** 規則選擇流程，例如台灣麻將的補花與花胡選擇。 */
    private val ruleDecisionResolverRegistry = RuleDecisionResolverRegistry().apply {
        BundledRiichiExtension.registerRuleDecisionResolvers(this)
        BundledTaiwanExtension.registerRuleDecisionResolvers(this)
        freeze()
    }

    /** 中止對局時記錄原因並移除對局。 */
    private val abortHandler = GameAbortHandler { expectedGame, reasonId, diagnostic ->
        if (gameRepository.getGame(expectedGame.id) != expectedGame) {
            false
        } else {
            abortDiagnostic = "$reasonId: $diagnostic"
            gameRepository.removeTableState(expectedGame.id)
            true
        }
    }

    /** 最終捨牌後的特殊結果，例如流局滿貫。 */
    private val postReactionRoundOutcomeResolverRegistry = PostReactionRoundOutcomeResolverRegistry().apply {
        BundledRiichiExtension.registerPostReactionRoundOutcomeResolvers(this)
        freeze()
    }

    /** 快照同步；快照只寫入測試替身。 */
    private val snapshotSynchronizer = GameSnapshotSynchronizer(gameRepository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(moduleRegistry, componentRegistry = bundledCoreExtensionRegistries().componentRegistry))

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
            BundledRiichiExtension.registerGameCommandHandlers(this)
            BundledTaiwanExtension.registerGameCommandHandlers(this)
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
            declareAndDiscardUseCase = DeclareAndDiscardUseCase(
                gameRepository = gameRepository,
                moduleRegistry = moduleRegistry,
                snapshotSynchronizer = snapshotSynchronizer,
                handSortPreferenceStore = handSortPreferenceStore,
                postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolverRegistry,
                eventPublisher = eventPublisher,
                presentationPublisher = presentationPublisher,

                componentRegistry = bundledCoreExtensionRegistries().componentRegistry,
            ),

            componentRegistry = bundledCoreExtensionRegistries().componentRegistry,
        ),
    )

    /** 把命令分派給各 use case。 */
    private val router = GameActionRouter(
        drawTileUseCase = DrawTileUseCase(gameRepository, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher, componentRegistry = bundledCoreExtensionRegistries().componentRegistry),
        discardTileUseCase = DiscardTileUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            handSortPreferenceStore,
            postActionExhaustiveDrawResolverRegistry,
            eventPublisher,
            presentationPublisher,

            componentRegistry = bundledCoreExtensionRegistries().componentRegistry,
        ),
        declareTsumoUseCase = DeclareTsumoUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            eventPublisher,
            presentationPublisher,
            winPresentationHandoff,
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,

            componentRegistry = bundledCoreExtensionRegistries().componentRegistry,
        ),
        declareKanUseCase = DeclareKanUseCase(gameRepository, moduleRegistry, snapshotSynchronizer, eventPublisher, presentationPublisher, componentRegistry = bundledCoreExtensionRegistries().componentRegistry),
        respondToDiscardUseCase = RespondToDiscardUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            handSortPreferenceStore,
            eventPublisher,
            presentationPublisher,
            winPresentationHandoff,
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,
            postActionExhaustiveDrawResolverRegistry = postActionExhaustiveDrawResolverRegistry,

            componentRegistry = bundledCoreExtensionRegistries().componentRegistry,
        ),
        respondToRobbingUseCase = RespondToRobbingUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            eventPublisher,
            presentationPublisher,
            winPresentationHandoff,
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = winSettlementDetailResolverRegistry,

            componentRegistry = bundledCoreExtensionRegistries().componentRegistry,
        ),
        declareAbortiveDrawUseCase = DeclareAbortiveDrawUseCase(gameRepository, moduleRegistry, snapshotSynchronizer, eventPublisher),
        extensionCommandExecutor = extensionCommandExecutor,
        submitRuleDecisionUseCase = SubmitRuleDecisionUseCase(
            gameRepository = gameRepository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = ruleDecisionResolverRegistry,
            snapshotSynchronizer = snapshotSynchronizer,
            presentationPublisher = presentationPublisher,
            winPresentationStager = RuleDecisionWinPresentationStager(winPresentationHandoff, winSettlementDetailResolverRegistry),
            handSortPreferenceStore = handSortPreferenceStore,
        ),
    )

    /** 在指定時間點開啟規則選擇視窗。 */
    val beginRuleDecisionUseCase = BeginRuleDecisionUseCase(gameRepository, moduleRegistry, ruleDecisionResolverRegistry, snapshotSynchronizer, abortHandler)

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
            ruleDecisionResolverRegistry = ruleDecisionResolverRegistry,
        ),
        advanceRoundUseCase = AdvanceRoundUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            handSortPreferenceStore,
            eventPublisher,
            presentationPublisher,
            componentRegistry = bundledCoreExtensionRegistries().componentRegistry,
            beginRuleDecisionUseCase = beginRuleDecisionUseCase,
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
            GameVisibilityPolicyImpl(moduleRegistry, componentRegistry = bundledCoreExtensionRegistries().componentRegistry),
            moduleRegistry,
            AiDecisionExecutor.direct(failurePolicy = ExtensionFailurePolicy.Default),
            AutomaticGameCommandResolver(moduleRegistry),
        ),
        forcedAutoPlayDriver = ForcedAutoPlayDriver(
            gameRepository,
            AutomaticGameCommandResolver(moduleRegistry),
        ),
        decisionAvailabilityService = GameDecisionAvailabilityService(
            presentationBusyGate,
            decisionTimerManager,
            DecisionTimerSynchronizationService(decisionTimerManager, gameRepository, FakeDecisionTimerUpdatePublisher()),
        ),
        presentationBusyGate = presentationBusyGate,
        exhaustiveDrawSettlementPresentationService = ExhaustiveDrawSettlementPresentationService(presentationPublisher),
        winPresentationHandoff = winPresentationHandoff,
        presentationPublisher = presentationPublisher,
        abortGameUseCase = abortHandler,
        automaticCommandResolver = AutomaticGameCommandResolver(moduleRegistry),
        ruleDecisionAiDriver = RuleDecisionAiDriver(
            gameRepository,
            moduleRegistry,
            ruleDecisionResolverRegistry,
            aiStrategyRegistry,
            GameVisibilityPolicyImpl(moduleRegistry, componentRegistry = bundledCoreExtensionRegistries().componentRegistry),
            AiDecisionExecutor.direct(failurePolicy = ExtensionFailurePolicy.Default),
        ),
        advanceAutomaticRuleDecisionUseCase = AdvanceAutomaticRuleDecisionUseCase(
            gameRepository = gameRepository,
            moduleRegistry = moduleRegistry,
            resolverRegistry = ruleDecisionResolverRegistry,
            snapshotSynchronizer = snapshotSynchronizer,
            presentationPublisher = presentationPublisher,
            winPresentationStager = RuleDecisionWinPresentationStager(winPresentationHandoff, winSettlementDetailResolverRegistry),
            handSortPreferenceStore = handSortPreferenceStore,
        ),
        beginRuleDecisionUseCase = beginRuleDecisionUseCase,
    )

    /**
     * 寫入開局桌況與 AI 玩家，給規則選擇一般時間設定，並在局首開啟規則選擇視窗，與正式開局流程相同。
     *
     * @param state 開局桌況。
     * @param aiPlayerStrategyKeys 玩家與策略 key 的對應。
     */
    suspend fun start(state: TableState, aiPlayerStrategyKeys: Map<Uuid, String>) {
        gameRepository.setTableState(state, aiPlayerStrategyKeys)
        gameRepository.updateGame(state.id) { game ->
            checkNotNull(game).copy(flowConfig = game.flowConfig.copy(ruleDecision = RuleDecisionConfig(RULE_DECISION_TIME))) to Unit
        }
        beginRuleDecisionUseCase(state.id, RuleDecisionTiming.ROUND_START)
    }

    /**
     * 以伺服器的推進方式推進對局，直到沒有進展或整場結束待返回房間：每一輪先接手排定的流程（例如換局），沒有時才
     * 推進自動操作。伺服器的自動推進管理器每次推進都是這個順序；只呼叫一次自動推進時，規則選擇排定的換局會留給
     * 下一次推進。
     *
     * @param gameId 要推進的對局。
     */
    suspend fun driveLikeServer(gameId: Uuid) {
        repeat(MAX_ADVANCES) {
            val before = gameRepository.getGame(gameId) ?: return
            if (before.pendingTransition == PendingGameTransition.ReturnToRoom) return
            if (!coordinator.resumePendingGameTransition(gameId)) coordinator.driveAutomatedPlayers(gameId)
            if (gameRepository.getGame(gameId) == before) return
        }
    }

    /** 測試常數。 */
    private companion object {
        /** 規則選擇的一般時間設定。 */
        val RULE_DECISION_TIME = 30.seconds

        /** 推進輪數上限；每一輪都會讓對局前進，正常整場遠低於這個數字。 */
        const val MAX_ADVANCES = 10_000
    }
}
