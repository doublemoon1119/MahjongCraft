package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DefaultGameConfigProvider
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverAudienceSource
import com.doublemoon1119.mahjongcraft.flow.common.observer.service.ObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionReporter
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceFailureReporter
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceFollowUp
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortEffects
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.AchievementGrantGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.FabricAchievementGrantGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.FabricAchievementService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricSessionBackgroundScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricLoaderServerConfigPathProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigPathProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.FabricMahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.CommittedFactsDispatcher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.DecisionTimerUpdatePublisherImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.FabricGameEventDeliveryReporter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.FabricGameEventLocationSource
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.FabricGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.FabricGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.GameEventExclusions
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.GameEventPublisherImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.RoomEventPublisherImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TableOpeningPresentationOperationTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.AutomaticControlUpdateService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricAiDecisionReporter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricAutomatedAdvanceFailureReporter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricAutomatedAdvanceFollowUp
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricDecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricExhaustiveDrawSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricGameAbortEffects
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricGameCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricMatchSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationEffectScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationShowcaseScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.GameActionCandidateResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongAutoDrawService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongTableGameActionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.PlayerDecisionPromptFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.ReadinessAnalysisDtoMapper
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.SpectatorObservationService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.FabricDebugCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.animation.FabricDebugAnimationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.FabricDebugDecisionCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.FabricDebugHistoryCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.FabricHistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.HistoryGenerationController
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history.HistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.FabricDebugPresentationCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression.FabricDebugProgressionCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.progression.FabricDebugRoundCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioLoader
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioPresentationPublisher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioPresentationSynchronizer
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioValidator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.FabricDebugScenarioCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress.FabricDebugStressCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress.StressTestController
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress.StressTestEnvironmentFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPlayerTableScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugTilePreviewSupport
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugVirtualTableLayoutFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.text.FabricDebugTextCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryQueryService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryRetentionCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.StressTestHistorySource
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.AutomaticControlSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.FabricAutomaticControlSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.FabricObserverSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.PlayerIdentitySender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.RoundResultSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.notification.FabricGameConfigTextFormatter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.notification.FabricPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.observer.FabricObserverAudienceSource
import com.doublemoon1119.mahjongcraft.platform.fabric.server.observer.FabricObserverSnapshotBroadcastService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricMahjongDataStatus
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricTableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.PlayerConnectionLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.ServerPlayerIdentityStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.BuiltInDefaultGameConfigProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricMahjongLobbyInfoLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricMahjongLobbyInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricRoomCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.ReachableMahjongTableResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.RoomMemberCandidateResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricMahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricMahjongPlayerInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricMahjongRoundInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricMahjongTileSelectionConfirmPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTablePresentationCleaner
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableRemovalNotifier
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableStateDelivery
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableStateDeliveryEnvironment
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.OrphanedTableCleanupService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.PersistentTableOverlayCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TableCleanupQueue
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TableRemovalNotifier
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TableStateDeliveryEnvironment
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.FabricMahjongDiscardPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.FabricMahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.FabricMahjongTileWallPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricTickMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricWorldTickWaiter
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.flowServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.minecraftServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventDeliveryReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventLocationSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * Minecraft server 流程使用的 Fabric adapter 定義。
 *
 * Dedicated server 與 client 程序內的 integrated server 都需要此 module。它包含 [flowServerModule]、[minecraftServerModule]、
 * [fabricCommonModule] 與所有 server-side Fabric adapter，不包含 HUD、renderer 或其他 Minecraft client-only 類別。
 * [FabricExtensionRegistries] 與 [CoreExtensionRegistries] 把既有 registry 組成 bootstrap 註冊、診斷快照與凍結使用的集合，
 * 不建立第二份實例。
 */
val fabricServerModule = module {
    includes(flowServerModule, minecraftServerModule, fabricCommonModule)

    single<FabricExtensionRegistries>()
    single<CoreExtensionRegistries>()

    single<FabricServerHolder>()

    single<FabricAchievementGrantGateway>() bind AchievementGrantGateway::class
    single<FabricAchievementService>()

    single<FabricAppCoroutineScope>() bind AppCoroutineScope::class
    single<FabricCoroutineDispatchers>() bind CoroutineDispatchers::class
    single<FabricSessionBackgroundScope>()

    single<FabricLoaderServerConfigPathProvider>() bind FabricServerConfigPathProvider::class
    single<FabricServerConfigCommand>()
    single<FabricServerConfigManager>()

    single<FabricMahjongDiceRollPresenter>() bind MahjongDiceRollPresenter::class

    single<FabricEntitySpawnGateway>()
    single<MahjongTileCollisionService>()

    single<CommittedFactsDispatcher>()
    single<DecisionTimerUpdatePublisherImpl>() bind DecisionTimerUpdatePublisher::class
    single<FabricGameEventDeliveryReporter>() bind GameEventDeliveryReporter::class
    single<FabricGameEventLocationSource>() bind GameEventLocationSource::class
    single<FabricGamePresentationBusyGate>() bind GamePresentationBusyGate::class
    single<FabricGamePresentationPublisher>() bind GamePresentationPublisher::class
    single<GameEventExclusions>()
    single<GameEventPublisherImpl>() bind GameEventPublisher::class
    single<RoomEventPublisherImpl>() bind RoomEventPublisher::class
    single<TableOpeningPresentationOperationTracker>()
    single<TablePresentationBusyTracker>()

    single<AutomaticControlUpdateService>()
    single<FabricAiDecisionReporter>() bind AiDecisionReporter::class
    single<FabricAutomatedAdvanceFailureReporter>() bind AutomatedAdvanceFailureReporter::class
    single<FabricAutomatedAdvanceFollowUp>() bind AutomatedAdvanceFollowUp::class
    single<FabricGameAbortEffects>() bind GameAbortEffects::class
    single<FabricDecisionTimerScheduler>()
    single<FabricExhaustiveDrawSettlementPresentationScheduler>()
    single<FabricGameCommand>()
    single<FabricMatchSettlementPresentationScheduler>()
    single<FabricWinCelebrationEffectScheduler>()
    single<FabricWinCelebrationShowcaseScheduler>()
    single<FabricWinSettlementPresentationScheduler>()
    single<GameActionCandidateResolver>()
    single<MahjongAutoDrawService>()
    single<MahjongTableGameActionService>()
    single<PlayerDecisionPromptFactory>()
    single<ReadinessAnalysisDtoMapper>()
    single<SpectatorObservationService>()
    single<FabricDebugCommand>()
    single<FabricDebugAnimationCommand>()
    single<FabricDebugDecisionCommand>()
    single<FabricDebugHistoryCommand>()
    single<FabricHistoryGenerationRuntimeFactory>() bind HistoryGenerationRuntimeFactory::class
    single<HistoryGenerationController>()
    single<DebugWinRoundContinuationState>()
    single<DebugWinShowcaseOverride>()
    single<FabricDebugPresentationCommand>()
    single<FabricDebugProgressionCommand>()
    single<FabricDebugRoundCommand>()
    single<DebugGameScenarioLoader>()
    single<DebugGameScenarioPresentationPublisher>()
    single<DebugGameScenarioPresentationSynchronizer>()
    single<DebugGameScenarioRegistry>()
    single<DebugGameScenarioValidator>()
    single<FabricDebugScenarioCommand>()
    single<FabricDebugStressCommand>()
    single<StressTestController>()
    single<StressTestEnvironmentFactory>()
    single<DebugPlayerTableScope>()
    single<DebugPreviewEntityLifecycle>()
    single<DebugTilePreviewSupport>()
    single<DebugVirtualTableLayoutFactory>()
    single<FabricDebugTextCommand>()

    single<FabricHistoryCommand>()
    // 量測用 observer 只在壓力測試另建的實例傳入，正式組裝不提供。
    single {
        FabricHistoryOutboxWriter(
            store = get(),
            registries = get(),
            dispatchers = get(),
            moduleRegistry = get(),
            locations = get(),
            configState = get(),
            retentionCoordinator = get(),
        )
    }
    single<FabricHistoryQueryService>()
    single<HistoryRetentionCoordinator>()
    single<StressTestHistorySource>()

    single<FabricAutomaticControlSnapshotSender>() bind AutomaticControlSnapshotSender::class
    single<FabricObserverSnapshotSender>() bind ObserverSnapshotSender::class
    single<PlayerIdentitySender>()
    single<RoundResultSender>()

    single<FabricGameConfigTextFormatter>()
    single<FabricPlayerFeedbackPublisher>() bind MinecraftPlayerFeedbackPublisher::class

    single<FabricObserverAudienceSource>() bind ObserverAudienceSource::class
    single<FabricObserverSnapshotBroadcastService>()

    single<FabricAuthoritativeStatePersistence>()
    single<FabricMahjongDataStatus>()
    single<FabricTableLocationPersistence>()

    single<PlayerConnectionLifecycleService>()
    single<ServerPlayerIdentityStore>()

    single<BuiltInDefaultGameConfigProvider>() bind DefaultGameConfigProvider::class
    single<FabricMahjongLobbyInfoLifecycleService>()
    single<FabricMahjongLobbyInfoPresenter>()
    single<FabricRoomCommand>()
    single<MahjongTableRoomService>()
    single<ReachableMahjongTableResolver>()
    single<RoomMemberCandidateResolver>()

    single<FabricMahjongSeatingPresenter>() bind MahjongSeatingPresenter::class
    single<FabricSeatProviderRegistryImpl>() bind FabricSeatProviderRegistry::class
    single<MahjongStoolSeatProvider>() bind SeatProvider::class
    single<MahjongStoolSeatService>()

    single<FabricMahjongPlayerInfoPresenter>() bind MahjongPlayerInfoPresenter::class
    single<FabricMahjongRoundInfoPresenter>() bind MahjongRoundInfoPresenter::class
    single<FabricMahjongTileSelectionConfirmPresenter>() bind MahjongTileSelectionConfirmPresenter::class
    single<FabricTableLifecycleService>()
    single<FabricTableLocationValidationService>()
    single<FabricTablePresentationCleaner>()
    single<FabricTableRemovalNotifier>() bind TableRemovalNotifier::class
    single<FabricTableStateDelivery>()
    single<FabricTableStateDeliveryEnvironment>() bind TableStateDeliveryEnvironment::class
    single<OrphanedTableCleanupService>()
    single<PersistentTableOverlayCoordinator>()
    single<TableCleanupQueue>()
    single<FabricTablePropKindRegistryImpl>() bind FabricTablePropKindRegistry::class
    single<FabricTablePropPresenter>() bind TablePropPresenter::class

    single<FabricMahjongDiscardPresenter>() bind MahjongDiscardPresenter::class
    single<FabricMahjongPlayerAreaPresenter>() bind MahjongPlayerAreaPresenter::class
    single<FabricMahjongTileWallPresenter>() bind MahjongTileWallPresenter::class

    single<FabricTickMonotonicClock>() bind MonotonicClock::class
    single<FabricWorldTickWaiter>()
}
