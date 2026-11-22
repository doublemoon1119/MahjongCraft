package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.HandSortPreferenceUpdateMode
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SetHandSortPreferenceUseCase
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.api.table.MahjongTables
import com.doublemoon1119.mahjongcraft.platform.fabric.development.MahjongCraftDevelopmentTools
import com.doublemoon1119.mahjongcraft.platform.fabric.di.fabricClientModule
import com.doublemoon1119.mahjongcraft.platform.fabric.di.fabricServerModule
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.fabric.loader.FabricModRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.loader.FabricPacketTransport
import com.doublemoon1119.mahjongcraft.platform.fabric.loader.FabricServerEvents
import com.doublemoon1119.mahjongcraft.platform.fabric.metadata.FabricRuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.network.C2SChannel
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModBlocks
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItemGroups
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.fabric.server.ServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.AiCharacterManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.SeatedParticipantService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.WorldParticipantPresenceService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerLifecycleAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.ServerThreadCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.ServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.CommittedFactsDispatcher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.ServerThreadGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.InGameCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongTableGameActionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.WinCelebrationEffectScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryQueryService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.AiCharacterSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.observer.ObserverSnapshotBroadcastService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.TableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.PlayerConnectionLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.ServerPlayerIdentityStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongLobbyInfoLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.RoomCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.AiSeatPlacementService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TablePropRefreshScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.TableSpaceObstructionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.TickMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.WorldTickWaiter
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModPackets
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModAchievements
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.ServerStopRunner
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.ServerStopRunner.StopStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.SessionBackgroundScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.historyRecordingPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.TableOpeningPresentationOperationTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.AutomaticControlUpdateService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryDatabasePath
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.AuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongDataLoadCause
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongDataProblem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongDataSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongDataStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.attachPersistenceState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import org.koin.core.Koin
import org.koin.core.context.startKoin
import kotlin.uuid.toKotlinUuid

/** Fabric mod 的初始化入口；組裝 runtime graph、註冊平台內容與伺服器事件。 */
class MahjongCraftMod : ModInitializer {

    /** Fabric 啟動、伺服器生命週期與命令處理的記錄器。 */
    private val logger = mahjongCraftLogger(MahjongCraftMod::class)

    override fun onInitialize() {
        // 頻道在建立時就會用到收送方式，必須在任何頻道建立前安裝。
        ModPackets.install(FabricPacketTransport)
        val koin = startDependencyInjection()
        // 開發工具的服務要在擴充登記之前載入，開發工具登記的擴充才能取用。
        val developmentTools = MahjongCraftDevelopmentTools.discover(koin.get<MinecraftEnvironment>())
        koin.loadModules(developmentTools.flatMap { it.koinModules() })
        // Koin single 建立後，必須先於 NetworkJson 與遊戲流程服務第一次被解析前完成。
        FabricMahjongExtensions.initialize(
            coreRegistries = koin.get<CoreExtensionRegistries>(),
            presentationRegistries = koin.get<MinecraftPresentationRegistries>(),
            fabricRegistries = koin.get<FabricExtensionRegistries>(),
            mahjongStoolSeatProvider = koin.get<MahjongStoolSeatProvider>(),
            failurePolicy = koin.get<ExtensionFailurePolicy>(),
        )
        ModItems.register(FabricModRegistrar)
        ModSounds.register(FabricModRegistrar)
        ModEntities.register(FabricModRegistrar)
        ModAchievements.register(FabricModRegistrar)
        val characterSnapshots = koin.get<AiCharacterSnapshotSender>()
        characterSnapshots.registerEvents(FabricServerEvents)
        val aiCharacters = koin.get<AiCharacterManager>()
        aiCharacters.registerEvents(FabricServerEvents)
        val participantPresence = koin.get<WorldParticipantPresenceService>()
        participantPresence.registerEvents(FabricServerEvents)
        val seatedParticipants = koin.get<SeatedParticipantService>()
        seatedParticipants.registerEvents(FabricServerEvents)
        koin.get<AiSeatPlacementService>().registerEvents(FabricServerEvents)
        val tableLifecycleService = koin.get<TableLifecycleService>()
        val tableLocationValidation = koin.get<TableLocationValidationService>()
        val tablePropRefreshScheduler = koin.get<TablePropRefreshScheduler>()
        val tableSpaceObstruction = koin.get<TableSpaceObstructionService>()
        MahjongTables.install(
            roomService = koin.get<MahjongTableRoomService>(),
            tableLifecycleService = tableLifecycleService,
        )
        ModBlocks.register(registrar = FabricModRegistrar, seatService = koin.get<MahjongStoolSeatService>())
        ModItemGroups.register(FabricModRegistrar)
        tableLifecycleService.registerEvents(FabricServerEvents)
        tableLocationValidation.registerEvents(FabricServerEvents)
        tablePropRefreshScheduler.registerEvents(FabricServerEvents)
        tableSpaceObstruction.registerEvents(FabricServerEvents)
        val lobbyInfoLifecycle = koin.get<MahjongLobbyInfoLifecycleService>()
        lobbyInfoLifecycle.registerEvents(FabricServerEvents)
        koin.get<DecisionTimerScheduler>().registerEvents(FabricServerEvents)
        koin.get<TickMonotonicClock>().registerEvents(FabricServerEvents)
        koin.get<WorldTickWaiter>().registerEvents(FabricServerEvents)
        koin.get<WinCelebrationEffectScheduler>().registerEvents(FabricServerEvents)
        registerDecisionSelectionReceiver(koin)

        val serverHolder = koin.get<ServerHolder>()
        val playerIdentityStore = koin.get<ServerPlayerIdentityStore>()
        val appScope = koin.get<ServerLifecycleAppCoroutineScope>()
        val backgroundScope = koin.get<SessionBackgroundScope>()
        val serverThread = koin.get<ServerThreadCoroutineDispatchers>().main
        val stopRunner = ServerStopRunner(mainDispatcher = serverThread, drain = serverThread)
        val advanceManager = koin.get<AutomatedAdvanceManager>()
        val stateCleaner = koin.get<ServerSessionStateCleaner>()
        val statePersistence = koin.get<AuthoritativeStatePersistence>()
        val historyWriter = koin.get<HistoryOutboxWriter>()
        val historyCommand = koin.get<HistoryCommand>()
        val decisionTimerManager = koin.get<GameDecisionTimerManager>()
        val tableLocationPersistence = koin.get<TableLocationPersistence>()
        val configManager = koin.get<ServerConfigManager>()
        val mahjongTileCollisionService = koin.get<MahjongTileCollisionService>()
        val openingPresentationOperations = koin.get<TableOpeningPresentationOperationTracker>()
        val gamePresentationPublisher = koin.get<ServerThreadGamePresentationPublisher>()
        val presentationBusyTracker = koin.get<TablePresentationBusyTracker>()
        val observerBroadcast = koin.get<ObserverSnapshotBroadcastService>()
        val committedFactsDispatcher = koin.get<CommittedFactsDispatcher>()
        val dataStatus = koin.get<MahjongDataStatus>()
        mahjongTileCollisionService.registerEvents(FabricServerEvents)
        observerBroadcast.registerEvents(FabricServerEvents)
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            dataStatus.clear()
            initializeServerConfig(configManager, mahjongTileCollisionService, server)
            // 房間與對局無法讀取時不中斷啟動：世界照常進入，權威狀態拒絕寫入，依賴它的服務本次不啟動。
            val mahjongAvailable = runBlocking { statePersistence.attachOrDisable(server) }
            if (mahjongAvailable) {
                runBlocking {
                    attachPersistenceState(
                        load = { Unit },
                        initialize = {
                            koin.get<AuthoritativeStateStore>().applyHistoryRecordingPolicy(configManager.current.historyRecordingPolicy())
                            tableLocationPersistence.attach(server)
                            if (!historyWriter.attach(server)) {
                                dataStatus.report(
                                    problem = MahjongDataProblem(
                                        source = MahjongDataSource.HISTORY_DATABASE,
                                        file = HistoryDatabasePath.displayPath,
                                        cause = MahjongDataLoadCause.Unreadable(historyWriter.lastError ?: "unknown"),
                                    ),
                                )
                            }
                        },
                        cleanupFailedAttach = {
                            // 啟動失敗只解除接線及記憶體；先停 listener，避免清理被寫成空存檔。
                            withContext(NonCancellable) {
                                historyWriter.detach()
                                tableLocationPersistence.detach()
                                statePersistence.detach()
                                stateCleaner.clear()
                            }
                        },
                    )
                }
            }
            if (mahjongAvailable) tableLocationValidation.startSession(server)
            if (mahjongAvailable) tablePropRefreshScheduler.startSession(server)
            if (mahjongAvailable) tableSpaceObstruction.startSession(server)
            if (mahjongAvailable) gamePresentationPublisher.startTablePropSession()
            serverHolder.set(server)
            playerIdentityStore.refresh()
            appScope.startSession()
            backgroundScope.startSession()
            if (mahjongAvailable) {
                advanceManager.startSession()
                observerBroadcast.startSession()
                committedFactsDispatcher.startSession(server)
                lobbyInfoLifecycle.startSession()
                aiCharacters.startSession(server)
                participantPresence.startSession(server)
                seatedParticipants.startSession(server)
            }
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            characterSnapshots.clear()
            seatedParticipants.stopSession()
            participantPresence.stopSession()
            aiCharacters.stopSession()
            tableLocationValidation.stopSession()
            tablePropRefreshScheduler.stopSession()
            tableSpaceObstruction.stopSession()
            // 不再開始新的自動推進，並直接捨棄不需要等待的背景工作（狀態訂閱、呈現、斷線倒數、歷史查詢）。
            advanceManager.stopSession()
            backgroundScope.cancel()
            // 收尾以主執行緒協程執行，等待期間主執行緒持續處理排入的工作：在途協程能完成，被取消的協程也能
            // 回到主執行緒釋放持有的鎖。超過時限時記錄停在哪一步並繼續關服。
            stopRunner.run(
                steps = listOf(
                    // appScope.shutdown() 必須先結束：已開始的權威工作（例如等鎖中的摸牌或反應結果）完成後，
                    // 才結算計時器並解除 persistence dirty listener，避免該次權威狀態變更在中途被取消而遺失。
                    StopStep("shutdown application scope") { appScope.shutdown() },
                    StopStep("stop observer broadcast") { observerBroadcast.stopSession() },
                    StopStep("stop committed facts dispatch") { committedFactsDispatcher.stopSession() },
                    // 已開始的權威工作結束並停止事件接收後，才清除事件投影使用的位置索引。
                    StopStep("detach table locations") { tableLocationPersistence.detach() },
                    StopStep("stop lobby info") { lobbyInfoLifecycle.stopSession() },
                    StopStep("clear presentation busy state") { presentationBusyTracker.clearAll() },
                    StopStep("clear opening presentation operations") { openingPresentationOperations.clearAll() },
                    StopStep("clear opening presentation state") { gamePresentationPublisher.clearOpeningSession() },
                    StopStep("settle decision timers") { decisionTimerManager.settleAll() },
                    StopStep("detach history writer") { historyWriter.detach() },
                    StopStep("detach authoritative state") { statePersistence.detach() },
                    StopStep("clear session state") { stateCleaner.clear() },
                ),
            )
            configManager.detach()
            serverHolder.clear()
            playerIdentityStore.refresh()
            dataStatus.clear()
        }

        registerRoomActionReceiver(koin)
        registerRequestSnapshotReceiver(koin)
        registerSetAutoSortHandReceiver(koin)
        registerAutomaticControlUpdateReceiver(koin)
        registerPlayerConnectionEvents(koin)
        koin.get<ServerConfigCommand>().register(FabricServerEvents)
        historyCommand.register(FabricServerEvents)
        koin.get<HistoryQueryService>().register(FabricServerEvents)
        koin.get<RoomCommand>().register(FabricServerEvents)
        koin.get<InGameCommand>().register(FabricServerEvents)
        developmentTools.forEach { it.initialize(koin, FabricServerEvents) }

        logger.info(koin.get<FabricRuntimeMetadata>().initializationMessage())
    }

    /**
     * 將 RoomScreen 的受控操作交給桌級房間服務執行。
     *
     * @param koin 目前 Fabric runtime 的依賴容器。
     */
    private fun registerRoomActionReceiver(koin: Koin) {
        val json = koin.get<NetworkJson>()
        val service = koin.get<MahjongTableRoomService>()
        MahjongChannels.roomAction.registerServerReceiver(json) { _, player, action ->
            service.handleRoomAction(player, action)
        }
    }

    /**
     * 依目前 Fabric environment 啟動 dedicated-server 或 client/integrated-server graph。
     *
     * Minecraft client 可啟動 integrated server，因此 client graph 同時包含 [fabricServerModule] 與 [fabricClientModule]；
     * 連線到 dedicated server 時 server-side single 維持延遲建立，不會建立第二個 Koin container。Dedicated server 只載入
     * [fabricServerModule]，不登記 client state。
     *
     * @return 對應執行環境的 Koin 依賴容器。
     */
    private fun startDependencyInjection(): Koin = if (FabricLoader.getInstance().environmentType == EnvType.CLIENT) {
        startKoin { modules(fabricServerModule, fabricClientModule) }.koin
    } else {
        startKoin { modules(fabricServerModule) }.koin
    }

    /**
     * 載入 server TOML；失敗時 manager 會保留程式預設值並記錄詳細錯誤。
     *
     * @param configManager 伺服器設定載入器與持久化管理器。
     * @param mahjongTileCollisionService 將設定套用至已載入麻將牌的服務。
     * @param server 目前 Minecraft server。
     */
    private fun initializeServerConfig(
        configManager: ServerConfigManager,
        mahjongTileCollisionService: MahjongTileCollisionService,
        server: MinecraftServer,
    ) {
        when (val result = configManager.attach(server)) {
            is MinecraftServerConfigUpdateResult.Success -> {
                mahjongTileCollisionService.applyToLoaded(server, result.config)
                // 使用 SLF4J 參數化訊息，避免對應 log level 關閉時先建立字串。
                if (result.createdDefaultFile) {
                    logger.info("Created and loaded default server config at {}", configManager.path)
                } else {
                    logger.info("Loaded server config from {}", configManager.path)
                }
            }

            is MinecraftServerConfigUpdateResult.Failure -> {
                mahjongTileCollisionService.applyToLoaded(server, MinecraftServerConfig())
                logger.warn("Using built-in MahjongCraft server config defaults because loading failed")
            }
        }
    }

    /**
     * 將 Fabric 玩家連線事件轉送給可測試的斷線政策執行器。
     *
     * @param koin 目前 Fabric runtime 的依賴容器。
     */
    private fun registerPlayerConnectionEvents(koin: Koin) {
        val lifecycleService = koin.get<PlayerConnectionLifecycleService>()
        val dataStatus = koin.get<MahjongDataStatus>()
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            dataStatus.notifyOnJoin(handler.player)
            if (dataStatus.isMahjongAvailable) lifecycleService.onConnected(handler.player.uuid.toKotlinUuid())
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val playerId = handler.player.uuid.toKotlinUuid()
            C2SChannel.forgetPlayer(playerId)
            if (dataStatus.isMahjongAvailable) lifecycleService.onDisconnected(playerId)
        }
    }

    /**
     * 接收操作 HUD 的候選選擇，實際合法性仍由伺服器目前 prompt 與正式流程驗證。
     *
     * @param koin 目前 Fabric runtime 的依賴容器。
     */
    private fun registerDecisionSelectionReceiver(koin: Koin) {
        val json = koin.get<NetworkJson>()
        val service = koin.get<MahjongTableGameActionService>()
        MahjongChannels.decisionSelection.registerServerReceiver(json) { _, player, selection ->
            service.select(player, selection)
        }
    }

    /**
     * 接收客戶端加入世界後主動送出的快照補送請求，見 [MahjongChannels.requestSnapshot] KDoc。
     *
     * @param koin 目前 Fabric runtime 的依賴容器。
     */
    private fun registerRequestSnapshotReceiver(koin: Koin) {
        val json = koin.get<NetworkJson>()
        val lifecycleService = koin.get<PlayerConnectionLifecycleService>()
        val dataStatus = koin.get<MahjongDataStatus>()
        // 客戶端進入世界時自動送出，麻將停用時直接略過、不提示玩家。
        MahjongChannels.requestSnapshot.registerServerReceiver(json) { _, player, _ ->
            if (!dataStatus.isMahjongAvailable) return@registerServerReceiver
            lifecycleService.onSnapshotRequested(player.uuid.toKotlinUuid())
        }
    }

    /**
     * 接收客戶端切換「自動整理手牌」偏好的請求，見 [MahjongChannels.setAutoSortHand] KDoc。
     *
     * @param koin 目前 Fabric runtime 的依賴容器。
     */
    private fun registerSetAutoSortHandReceiver(koin: Koin) {
        val json = koin.get<NetworkJson>()
        val useCase = koin.get<SetHandSortPreferenceUseCase>()
        val scope = koin.get<AppCoroutineScope>()
        val dataStatus = koin.get<MahjongDataStatus>()
        // 排序偏好由客戶端自動同步，麻將停用時直接略過、不提示玩家。
        MahjongChannels.restoreAutoSortHand.registerServerReceiver(json) { _, player, enabled ->
            if (!dataStatus.isMahjongAvailable) return@registerServerReceiver
            scope.launch {
                useCase(player.uuid.toKotlinUuid(), enabled, HandSortPreferenceUpdateMode.RESTORE)
            }
        }
        MahjongChannels.setAutoSortHand.registerServerReceiver(json) { _, player, enabled ->
            if (!dataStatus.isMahjongAvailable) return@registerServerReceiver
            scope.launch {
                useCase(player.uuid.toKotlinUuid(), enabled, HandSortPreferenceUpdateMode.USER_CHANGE)
            }
        }
    }

    /**
     * 接收本局自動操作集合更新，只使用連線玩家身分並回覆可配對的權威結果；也接收等不到回覆時的重新同步要求。
     *
     * @param koin 目前 Fabric runtime 的依賴容器。
     */
    private fun registerAutomaticControlUpdateReceiver(koin: Koin) {
        val json = koin.get<NetworkJson>()
        val service = koin.get<AutomaticControlUpdateService>()
        val scope = koin.get<AppCoroutineScope>()
        val lifecycleService = koin.get<PlayerConnectionLifecycleService>()
        val dataStatus = koin.get<MahjongDataStatus>()
        MahjongChannels.automaticControlUpdate.registerServerReceiver(json) { _, player, request ->
            scope.launch {
                service(player.uuid.toKotlinUuid(), request)?.let { result ->
                    MahjongChannels.automaticControlUpdateResult.sendTo(player, json, result)
                }
            }
        }
        MahjongChannels.requestAutomaticControlSnapshot.registerServerReceiver(json) { _, player, _ ->
            if (!dataStatus.isMahjongAvailable) return@registerServerReceiver
            lifecycleService.onAutomaticControlSnapshotRequested(player.uuid.toKotlinUuid())
        }
    }
}
