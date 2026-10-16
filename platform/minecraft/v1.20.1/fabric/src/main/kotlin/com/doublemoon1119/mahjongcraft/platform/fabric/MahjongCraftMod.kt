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
import com.doublemoon1119.mahjongcraft.platform.fabric.di.fabricClientModule
import com.doublemoon1119.mahjongcraft.platform.fabric.di.fabricServerModule
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.metadata.FabricRuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.network.C2SChannel
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModAchievements
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModBlocks
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItemGroups
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModSounds
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricServerStopRunner
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricServerStopRunner.StopStep
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricSessionBackgroundScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.historyRecordingPolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.CommittedFactsDispatcher
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TableOpeningPresentationOperationTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.AutomaticControlUpdateService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricDecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricGameCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationEffectScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongTableGameActionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.FabricDebugCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryDatabasePath
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryQueryService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.observer.FabricObserverSnapshotBroadcastService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricMahjongDataStatus
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricTableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.MahjongDataLoadCause
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.MahjongDataProblem
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.MahjongDataSource
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.attachPersistenceState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.PlayerConnectionLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.ServerPlayerIdentityStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricMahjongLobbyInfoLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricRoomCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricTickMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
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
        val koin = startDependencyInjection()
        // Koin single 建立後，必須先於 NetworkJson 與遊戲流程服務第一次被解析前完成。
        FabricMahjongExtensions.initialize(
            coreRegistries = koin.get<CoreExtensionRegistries>(),
            presentationRegistries = koin.get<MinecraftPresentationRegistries>(),
            fabricRegistries = koin.get<FabricExtensionRegistries>(),
            mahjongStoolSeatProvider = koin.get<MahjongStoolSeatProvider>(),
            debugWinRoundContinuationState = koin.get<DebugWinRoundContinuationState>(),
            minecraftEnvironment = koin.get<MinecraftEnvironment>(),
            failurePolicy = koin.get<ExtensionFailurePolicy>(),
        )
        ModItems.register()
        ModSounds.register()
        ModEntities.register()
        ModAchievements.register()
        val tableLifecycleService = koin.get<FabricTableLifecycleService>()
        val tableLocationValidation = koin.get<FabricTableLocationValidationService>()
        ModBlocks.register(
            roomService = koin.get<MahjongTableRoomService>(),
            tableLifecycleService = tableLifecycleService,
            seatService = koin.get<MahjongStoolSeatService>(),
        )
        ModItemGroups.register()
        tableLifecycleService.registerEvents()
        tableLocationValidation.registerEvents()
        val lobbyInfoLifecycle = koin.get<FabricMahjongLobbyInfoLifecycleService>()
        lobbyInfoLifecycle.registerEvents()
        koin.get<FabricDecisionTimerScheduler>().registerEvents()
        koin.get<FabricTickMonotonicClock>().registerEvents()
        koin.get<FabricWinCelebrationEffectScheduler>().registerEvents()
        registerDecisionSelectionReceiver(koin)

        val serverHolder = koin.get<FabricServerHolder>()
        val playerIdentityStore = koin.get<ServerPlayerIdentityStore>()
        val appScope = koin.get<FabricAppCoroutineScope>()
        val backgroundScope = koin.get<FabricSessionBackgroundScope>()
        val serverThread = koin.get<FabricCoroutineDispatchers>().main
        val stopRunner = FabricServerStopRunner(mainDispatcher = serverThread, drain = serverThread)
        val advanceManager = koin.get<AutomatedAdvanceManager>()
        val stateCleaner = koin.get<ServerSessionStateCleaner>()
        val statePersistence = koin.get<FabricAuthoritativeStatePersistence>()
        val historyWriter = koin.get<FabricHistoryOutboxWriter>()
        val historyCommand = koin.get<FabricHistoryCommand>()
        val decisionTimerManager = koin.get<GameDecisionTimerManager>()
        val tableLocationPersistence = koin.get<FabricTableLocationPersistence>()
        val configManager = koin.get<FabricServerConfigManager>()
        val mahjongTileCollisionService = koin.get<MahjongTileCollisionService>()
        val openingPresentationOperations = koin.get<TableOpeningPresentationOperationTracker>()
        val presentationBusyTracker = koin.get<TablePresentationBusyTracker>()
        val observerBroadcast = koin.get<FabricObserverSnapshotBroadcastService>()
        val committedFactsDispatcher = koin.get<CommittedFactsDispatcher>()
        val dataStatus = koin.get<FabricMahjongDataStatus>()
        mahjongTileCollisionService.registerEvents()
        observerBroadcast.registerEvents()
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
                                    MahjongDataProblem(
                                        source = MahjongDataSource.HISTORY_DATABASE,
                                        file = FabricHistoryDatabasePath.displayPath,
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
            serverHolder.set(server)
            playerIdentityStore.refresh()
            appScope.startSession()
            backgroundScope.startSession()
            if (mahjongAvailable) {
                advanceManager.startSession()
                observerBroadcast.startSession()
                committedFactsDispatcher.startSession(server)
                lobbyInfoLifecycle.startSession()
            }
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            tableLocationValidation.stopSession()
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
        koin.get<FabricServerConfigCommand>().register()
        historyCommand.register()
        koin.get<FabricHistoryQueryService>().register()
        koin.get<FabricRoomCommand>().register()
        koin.get<FabricGameCommand>().register()
        koin.get<FabricDebugCommand>().register()

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
        configManager: FabricServerConfigManager,
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
        val dataStatus = koin.get<FabricMahjongDataStatus>()
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
        val dataStatus = koin.get<FabricMahjongDataStatus>()
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
        val dataStatus = koin.get<FabricMahjongDataStatus>()
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
     * 接收本局自動操作集合更新，只使用連線玩家身分並回覆可配對的權威結果。
     *
     * @param koin 目前 Fabric runtime 的依賴容器。
     */
    private fun registerAutomaticControlUpdateReceiver(koin: Koin) {
        val json = koin.get<NetworkJson>()
        val service = koin.get<AutomaticControlUpdateService>()
        val scope = koin.get<AppCoroutineScope>()
        MahjongChannels.automaticControlUpdate.registerServerReceiver(json) { _, player, request ->
            scope.launch {
                service(player.uuid.toKotlinUuid(), request)?.let { result ->
                    MahjongChannels.automaticControlUpdateResult.sendTo(player, json, result)
                }
            }
        }
    }
}
