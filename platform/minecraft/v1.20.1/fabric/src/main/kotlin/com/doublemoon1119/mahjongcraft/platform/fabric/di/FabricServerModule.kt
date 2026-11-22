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
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterCapabilityRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricRoomParticipantRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricSeatPlacementRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricSeatProviderRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.server.ServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement.AdvancementAchievementGrantGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.AiCharacterBindings
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.AiCharacterCapabilityRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.AiCharacterManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.AiCharacterTracking
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.RoomParticipantRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.SeatedParticipantService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.WorldParticipantPresenceService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerLifecycleAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerThreadCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricLoaderServerConfigPathProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.ServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.EntityMahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.CommittedFactsDispatcher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.DecisionTimerUpdatePublisherImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.GameEventPublisherImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.RoomEventPublisherImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.ServerThreadGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TrackerGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.AutoDrawAutomatedAdvanceFollowUp
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.ExhaustiveDrawSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.InGameCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongAutoDrawService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongTableGameActionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MatchSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.TablePresentationGameAbortEffects
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.WinCelebrationEffectScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.WinCelebrationShowcaseScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.WinSettlementPresentationScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryQueryService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.StressTestHistorySource
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.AiCharacterSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.AutomaticControlSnapshotPacketSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.ObserverSnapshotPacketSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.PlayerIdentitySender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.PrivateHandOrderSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.RoundResultSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.notification.PlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.observer.ChunkTrackingObserverAudienceSource
import com.doublemoon1119.mahjongcraft.platform.fabric.server.observer.ObserverSnapshotBroadcastService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.TableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.PlayerConnectionLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.ServerPlayerIdentityStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongLobbyInfoLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongLobbyInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.RoomCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.RoomMemberCandidateResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.AiSeatPlacementService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.EntityMahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.SeatPlacementRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.SeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.SeatingSessionFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.EntityMahjongPlayerInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.EntityMahjongRoundInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.EntityMahjongTileSelectionConfirmPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.PersistentTableOverlayCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.ServerTableStateDeliveryEnvironment
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TablePresentationCleaner
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TablePropRefreshScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TableSpaceObstructionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.EntityTablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.TablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.TablePropKindRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.EntityMahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.EntityMahjongTileWallPresenter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.TickMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.WorldTickWaiter
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.flowServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.minecraftServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventDeliveryReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventLocationSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development.TableEndListeners
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development.WinCelebrationRequestAdjusters
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.achievement.AchievementGrantGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.achievement.AchievementService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.SessionBackgroundScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigPathProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.entity.EntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.GameEventExclusions
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.LoggingGameEventDeliveryReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.SnapshotGameEventLocationSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.TableOpeningPresentationOperationTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.AutomaticControlUpdateService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.GameActionCandidateResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.LoggingAiDecisionReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.LoggingAutomatedAdvanceFailureReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.PlayerDecisionPromptFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.ReadinessAnalysisDtoMapper
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.SpectatorObservationService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryRetentionCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.network.AiCharacterSnapshotDelivery
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.network.AutomaticControlSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.notification.GameConfigTextFormatter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.AuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongDataStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.player.ServerAiPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.BuiltInDefaultGameConfigProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.ReachableMahjongTableResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.BroadcastTableRemovalNotifier
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.OrphanedTableCleanupService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TableCleanupQueue
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TablePropRefreshQueue
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TableRemovalNotifier
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TableStateDelivery
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TableStateDeliveryEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
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

    single<RoomParticipantRegistry>() bind FabricRoomParticipantRegistrar::class
    single<AiCharacterCapabilityRegistry>() bind FabricAiCharacterCapabilityRegistrar::class
    single<SeatPlacementRegistry>() bind FabricSeatPlacementRegistrar::class
    single<AiSeatPlacementService>()
    single<FabricExtensionRegistries>()
    single<CoreExtensionRegistries>()

    single<ServerHolder>()

    single<AdvancementAchievementGrantGateway>() bind AchievementGrantGateway::class
    single<AchievementService>()

    single<ServerLifecycleAppCoroutineScope>() bind AppCoroutineScope::class
    single<ServerThreadCoroutineDispatchers>() bind CoroutineDispatchers::class
    single<SessionBackgroundScope>()

    single<FabricLoaderServerConfigPathProvider>() bind ServerConfigPathProvider::class
    single<ServerConfigCommand>()
    single<ServerConfigManager>()

    single<EntityMahjongDiceRollPresenter>() bind MahjongDiceRollPresenter::class

    single<EntitySpawnGateway>()
    single<MahjongTileCollisionService>()

    single<CommittedFactsDispatcher>()
    single<DecisionTimerUpdatePublisherImpl>() bind DecisionTimerUpdatePublisher::class
    single<LoggingGameEventDeliveryReporter>() bind GameEventDeliveryReporter::class
    single<SnapshotGameEventLocationSource>() bind GameEventLocationSource::class
    single<TrackerGamePresentationBusyGate>() bind GamePresentationBusyGate::class
    single<ServerThreadGamePresentationPublisher>() bind GamePresentationPublisher::class
    single<GameEventExclusions>()
    single<GameEventPublisherImpl>() bind GameEventPublisher::class
    single<RoomEventPublisherImpl>() bind RoomEventPublisher::class
    single<TableOpeningPresentationOperationTracker>()
    single<TablePresentationBusyTracker>()

    single<AutomaticControlUpdateService>()
    single<LoggingAiDecisionReporter>() bind AiDecisionReporter::class
    single<LoggingAutomatedAdvanceFailureReporter>() bind AutomatedAdvanceFailureReporter::class
    single<AutoDrawAutomatedAdvanceFollowUp>() bind AutomatedAdvanceFollowUp::class
    single<TablePresentationGameAbortEffects>() bind GameAbortEffects::class
    single<DecisionTimerScheduler>()
    single<ExhaustiveDrawSettlementPresentationScheduler>()
    single<InGameCommand>()
    single<MatchSettlementPresentationScheduler>()
    single<WinCelebrationEffectScheduler>()
    single<WinCelebrationShowcaseScheduler>()
    single<WinSettlementPresentationScheduler>()
    single<GameActionCandidateResolver>()
    single<MahjongAutoDrawService>()
    single<MahjongTableGameActionService>()
    // 呼叫時才取角色快照，讓讀檔還原期間也能建立操作提示，而不形成保存服務的依賴循環。
    single {
        ServerAiPlayerDisplayNameResolver { matchId ->
            get<AuthoritativeStatePersistence>().characterSnapshot()?.characters?.matchPlayers(matchId).orEmpty()
        }
    }
    single<PlayerDecisionPromptFactory>()
    single<ReadinessAnalysisDtoMapper>()
    single<SpectatorObservationService>()
    single<TableEndListeners>()
    single<WinCelebrationRequestAdjusters>()

    single<HistoryCommand>()
    // 量測用 observer 只在壓力測試另建的實例傳入，正式組裝不提供。
    single {
        HistoryOutboxWriter(
            store = get(),
            registries = get(),
            dispatchers = get(),
            moduleRegistry = get(),
            locations = get(),
            presentationNotes = get(),
            configState = get(),
            retentionCoordinator = get(),
            characterAppearance = { matchId, playerId -> get<AuthoritativeStatePersistence>().characterSnapshot()?.characters?.matchPlayers(matchId)?.get(playerId) },
        )
    }
    single<HistoryQueryService>()
    single<HistoryRetentionCoordinator>()
    single<StressTestHistorySource>()

    single<AutomaticControlSnapshotPacketSender>() bind AutomaticControlSnapshotSender::class
    single<ObserverSnapshotPacketSender>() bind ObserverSnapshotSender::class
    single<PlayerIdentitySender>()
    single<RoundResultSender>()
    single<PrivateHandOrderSender>()

    single<GameConfigTextFormatter>()
    single<PlayerFeedbackPublisher>() bind MinecraftPlayerFeedbackPublisher::class

    single<ChunkTrackingObserverAudienceSource>() bind ObserverAudienceSource::class
    single<ObserverSnapshotBroadcastService>()

    single<AuthoritativeStatePersistence>()
    single<MahjongDataStatus>()
    single<TableLocationPersistence>()

    single<PlayerConnectionLifecycleService>()
    single<ServerPlayerIdentityStore>()

    single<BuiltInDefaultGameConfigProvider>() bind DefaultGameConfigProvider::class
    single<MahjongLobbyInfoLifecycleService>()
    single<MahjongLobbyInfoPresenter>()
    single<RoomCommand>()
    single<MahjongTableRoomService>()
    single<ReachableMahjongTableResolver>()
    single<RoomMemberCandidateResolver>()

    single<AiCharacterSnapshotSender>() bind AiCharacterSnapshotDelivery::class
    single<AiCharacterTracking>()
    single<AiCharacterBindings>()
    single<SeatedParticipantService>()
    single<AiCharacterManager>()
    single<WorldParticipantPresenceService>()
    single<SeatingSessionFactory>()
    single<EntityMahjongSeatingPresenter>() bind MahjongSeatingPresenter::class
    single<SeatProviderRegistryImpl>() bind FabricSeatProviderRegistrar::class
    single<MahjongStoolSeatProvider>() bind SeatProvider::class
    single<MahjongStoolSeatService>()

    single<EntityMahjongPlayerInfoPresenter>() bind MahjongPlayerInfoPresenter::class
    single<EntityMahjongRoundInfoPresenter>() bind MahjongRoundInfoPresenter::class
    single<EntityMahjongTileSelectionConfirmPresenter>() bind MahjongTileSelectionConfirmPresenter::class
    single<TableLifecycleService>()
    single<TableLocationValidationService>()
    single<TablePropRefreshScheduler>()
    single<TableSpaceObstructionService>()
    single<TablePresentationCleaner>()
    single<BroadcastTableRemovalNotifier>() bind TableRemovalNotifier::class
    single<TableStateDelivery>()
    single<ServerTableStateDeliveryEnvironment>() bind TableStateDeliveryEnvironment::class
    single<OrphanedTableCleanupService>()
    single<PersistentTableOverlayCoordinator>()
    single<TableCleanupQueue>()
    single<TablePropRefreshQueue>()
    single<TablePropKindRegistryImpl>() bind TablePropKindRegistry::class
    single<EntityTablePropPresenter>() bind TablePropPresenter::class

    single<EntityMahjongPlayerAreaPresenter>() bind MahjongPlayerAreaPresenter::class
    single<EntityMahjongTileWallPresenter>() bind MahjongTileWallPresenter::class

    single<TickMonotonicClock>() bind MonotonicClock::class
    single<WorldTickWaiter>()
}
