package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiTurnDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceManager
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomaticDecisionDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomaticGameCommandResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ForcedAutoPlayDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameActionCommandMapper
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameActionRouter
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationAiDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.HandReadinessVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.service.AutomaticDecisionEvaluator
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.ExhaustiveDrawSettlementPresentationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimeoutService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AbortGameUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceAutomaticRoundPreparationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.BeginRoundPreparationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAbortiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAndDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetPlayerDecisionOptionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolvePostReactionRoundOutcomeUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolveWinRoundContinuationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToRobbingUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ReturnToRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SetHandSortPreferenceUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.StartGameUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SubmitRoundPreparationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SyncGameSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.UpdatePlayerAutomaticControlsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateRestorer
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.observer.ObserverSnapshotBroadcaster
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.AddAiPlayerUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.ChangeAiStrategyUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.CreateRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.JoinRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.KickPlayerUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.LeaveRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.SyncRoomSnapshotUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.ToggleReadyUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.UpdateConfigUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

/**
 * 平台為 `:mahjong-flow-server` 組裝的 Koin 定義。
 *
 * 各 registry 的內容由 extension bootstrap 登記。`:mahjong-ai` 刻意不依賴 Koin（比照 `:mahjong-logic` 維持框架無關），其 registry
 * 在這裡明確建立：AI 策略 registry 在未知或未指定策略時退回初級，內建策略由 extension bootstrap 登記；對手模型 registry 在規則
 * 沒有登記時使用不具規則知識的對手模型。AI 決策執行器的容量依 runtime 平行度計算，同時存在的策略工作上限為 AI 執行緒數的
 * [AiDecisionExecutor.CAPACITY_PER_THREAD] 倍；單局推進管理器以 [AutomatedAdvanceManager.forCoordinator] 將協調器組成推進
 * callback，在目前執行階段的作用域與權威狀態調度器上推進遊戲。
 */
val flowServerModule = module {
    includes(flowCommonModule)

    single<ExtensionGameActionAiRegistry>()
    single<MahjongAiStrategyRegistry> { MahjongAiStrategyRegistryImpl(defaultKey = BuiltInAiStrategyKeys.BEGINNER) }
    single<OpponentModelRegistry>()
    single {
        val dispatchers = get<CoroutineDispatchers>()
        AiDecisionExecutor(
            dispatcher = dispatchers.aiDecision,
            capacity = dispatchers.aiDecisionParallelism * AiDecisionExecutor.CAPACITY_PER_THREAD,
            reporter = get(),
            failurePolicy = get(),
        )
    }
    single {
        AutomatedAdvanceManager.forCoordinator(
            scope = get<AppCoroutineScope>(),
            dispatcher = get<CoroutineDispatchers>().main,
            coordinator = get(),
            followUp = get(),
            failureReporter = get(),
        )
    }

    factory<AiTurnDriver>()
    factory<AutomaticDecisionDriver>()
    single<AutomaticGameCommandResolver>()
    factory<AbortGameUseCase>() bind GameAbortHandler::class
    single<ExtensionGameActionCommandFactoryRegistry>()
    single<ExtensionGameCommandContext>()
    single<ExtensionGameCommandExecutor>()
    single<ExtensionGameCommandExecutorRegistry>()
    factory<ForcedAutoPlayDriver>()
    single<GameActionCommandMapper>()
    factory<GameActionRouter>()
    factory<GameFlowCoordinator>()
    single<PostActionExhaustiveDrawResolverRegistry>()
    single<PostReactionRoundOutcomeResolverRegistry>()
    factory<RoundPreparationAiDriver>()
    single<WinRoundContinuationResolverRegistry>()
    single<GameVisibilityPolicyImpl>() bind GameVisibilityPolicy::class
    single<HandReadinessVisibilityPolicy>()
    single<GameRepositoryImpl>() bind GameRepository::class
    factory<DeclareRiichiUseCase>()
    factory<DeclareAndDiscardUseCase>()
    single<AutomaticDecisionEvaluator>()
    single<DecisionTimerSynchronizationService>()
    factory<ExhaustiveDrawSettlementPresentationService>()
    single<GameDecisionAuthorityResolver>()
    single<GameDecisionAvailabilityService>()
    single<GameDecisionTimeoutService>()
    single<GameDecisionTimerManager>()
    single<GameSnapshotSynchronizer>()
    single<HandSortPreferenceStore>()
    single<PlayerActionContextResolver>()
    factory<PlayerDecisionTimerFactory>()
    single<WinPresentationHandoff>()
    single<WinSettlementDetailResolverRegistry>()
    factory<AdvanceAutomaticRoundPreparationUseCase>()
    factory<AdvanceRoundUseCase>()
    factory<BeginRoundPreparationUseCase>()
    factory<DeclareAbortiveDrawUseCase>()
    factory<DeclareExhaustiveDrawUseCase>()
    factory<DeclareKanUseCase>()
    factory<DeclareTsumoUseCase>()
    factory<DiscardTileUseCase>()
    factory<DrawTileUseCase>()
    factory<GetLegalActionsUseCase>()
    factory<GetPlayerDecisionOptionsUseCase>()
    factory<ResolvePostReactionRoundOutcomeUseCase>()
    factory<ResolveWinRoundContinuationUseCase>()
    factory<RespondToDiscardUseCase>()
    factory<RespondToRobbingUseCase>()
    factory<ReturnToRoomUseCase>()
    factory<SetHandSortPreferenceUseCase>()
    factory<StartGameUseCase>()
    factory<SubmitRoundPreparationUseCase>()
    factory<SyncGameSnapshotUseCase>()
    factory<UpdatePlayerAutomaticControlsUseCase>()

    single<ServerSessionStateCleaner>()
    single<ServerSessionStateRestorer>()

    single<PlayerMembershipRepositoryImpl>() bind PlayerMembershipRepository::class

    single<ObserverSnapshotBroadcaster>()

    single<RoomRepositoryImpl>() bind RoomRepository::class
    factory<AddAiPlayerUseCase>()
    factory<ChangeAiStrategyUseCase>()
    factory<CreateRoomUseCase>()
    factory<JoinRoomUseCase>()
    factory<KickPlayerUseCase>()
    factory<LeaveRoomUseCase>()
    factory<SyncRoomSnapshotUseCase>()
    factory<ToggleReadyUseCase>()
    factory<UpdateConfigUseCase>()

    // 使用建構子的預設設定；記錄政策由伺服器啟動流程套用，量測用 observer 只在壓力測試另建的實例傳入。
    single { AuthoritativeStateStore() }
}
