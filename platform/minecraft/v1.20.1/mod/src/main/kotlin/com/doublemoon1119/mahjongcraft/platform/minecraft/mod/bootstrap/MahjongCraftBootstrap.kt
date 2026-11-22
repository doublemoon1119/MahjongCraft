package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.bootstrap

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.HandSortPreferenceUpdateMode
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SetHandSortPreferenceUseCase
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.table.MahjongTables
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development.MahjongCraftDevelopmentEntrypoint
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.metadata.RuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.C2SChannel
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModAchievements
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModBlocks
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModItemGroups
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.ServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.character.AiCharacterManager
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.character.SeatedParticipantService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.character.WorldParticipantPresenceService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.ServerLifecycleAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.ServerStopRunner
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.ServerStopRunner.StopStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.ServerThreadCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.concurrency.SessionBackgroundScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.historyRecordingPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.CommittedFactsDispatcher
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.ServerThreadGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.TableOpeningPresentationOperationTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.AutomaticControlUpdateService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.DecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.InGameCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.MahjongTableGameActionService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.WinCelebrationEffectScheduler
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryDatabasePath
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history.HistoryQueryService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.network.AiCharacterSnapshotSender
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.observer.ObserverSnapshotBroadcastService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.AuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongDataLoadCause
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongDataProblem
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongDataSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongDataStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.TableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.attachPersistenceState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.player.PlayerConnectionLifecycleService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.player.ServerPlayerIdentityStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.MahjongLobbyInfoLifecycleService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.RoomCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.AiSeatPlacementService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.seating.MahjongStoolSeatService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TablePropRefreshScheduler
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TableSpaceObstructionService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.time.TickMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.time.WorldTickWaiter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.minecraft.server.MinecraftServer
import org.koin.core.Koin
import kotlin.uuid.toKotlinUuid

/**
 * 與載入器無關的 mod 初始化流程：註冊內容、掛上伺服器事件、網路接收器與指令。
 *
 * 載入器進入點先安裝自己的收送方式、啟動 Koin 並完成擴充登記，再呼叫 [initialize]；
 * 擴充登記必須早於這裡的內容註冊，擴充提供的座位等內容才能被本體使用。
 *
 * @property koin 目前執行環境的依賴容器。
 * @property events 載入器提供的伺服器事件。
 * @property registrar 載入器提供的內容註冊方式。
 */
class MahjongCraftBootstrap(
    private val koin: Koin,
    private val events: ServerEvents,
    private val registrar: ModRegistrar,
) {
    /** 初始化、伺服器生命週期與命令處理的記錄器。 */
    private val logger = mahjongCraftLogger(MahjongCraftBootstrap::class)

    /**
     * 註冊所有內容並掛上伺服器事件。
     *
     * @param developmentTools 開發環境找到的開發工具；正式環境為空。
     */
    fun initialize(developmentTools: List<MahjongCraftDevelopmentEntrypoint>) {
        ModItems.register(registrar)
        ModSounds.register(registrar)
        ModEntities.register(registrar)
        ModAchievements.register(registrar)
        val characterSnapshots = koin.get<AiCharacterSnapshotSender>()
        characterSnapshots.registerEvents(events)
        val aiCharacters = koin.get<AiCharacterManager>()
        aiCharacters.registerEvents(events)
        val participantPresence = koin.get<WorldParticipantPresenceService>()
        participantPresence.registerEvents(events)
        val seatedParticipants = koin.get<SeatedParticipantService>()
        seatedParticipants.registerEvents(events)
        koin.get<AiSeatPlacementService>().registerEvents(events)
        val tableLifecycleService = koin.get<TableLifecycleService>()
        val tableLocationValidation = koin.get<TableLocationValidationService>()
        val tablePropRefreshScheduler = koin.get<TablePropRefreshScheduler>()
        val tableSpaceObstruction = koin.get<TableSpaceObstructionService>()
        MahjongTables.install(
            roomService = koin.get<MahjongTableRoomService>(),
            tableLifecycleService = tableLifecycleService,
        )
        ModBlocks.register(registrar = registrar, seatService = koin.get<MahjongStoolSeatService>())
        ModItemGroups.register(registrar)
        tableLifecycleService.registerEvents(events)
        tableLocationValidation.registerEvents(events)
        tablePropRefreshScheduler.registerEvents(events)
        tableSpaceObstruction.registerEvents(events)
        val lobbyInfoLifecycle = koin.get<MahjongLobbyInfoLifecycleService>()
        lobbyInfoLifecycle.registerEvents(events)
        koin.get<DecisionTimerScheduler>().registerEvents(events)
        koin.get<TickMonotonicClock>().registerEvents(events)
        koin.get<WorldTickWaiter>().registerEvents(events)
        koin.get<WinCelebrationEffectScheduler>().registerEvents(events)
        registerDecisionSelectionReceiver()

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
        mahjongTileCollisionService.registerEvents(events)
        observerBroadcast.registerEvents(events)
        events.onServerStarted { server ->
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
        events.onServerStopping {
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

        registerRoomActionReceiver()
        registerRequestSnapshotReceiver()
        registerSetAutoSortHandReceiver()
        registerAutomaticControlUpdateReceiver()
        registerPlayerConnectionEvents()
        koin.get<ServerConfigCommand>().register(events)
        historyCommand.register(events)
        koin.get<HistoryQueryService>().register(events)
        koin.get<RoomCommand>().register(events)
        koin.get<InGameCommand>().register(events)
        developmentTools.forEach { it.initialize(koin, events) }

        logger.info(koin.get<RuntimeMetadata>().initializationMessage())
    }

    /**
     * 將 RoomScreen 的受控操作交給桌級房間服務執行。
     */
    private fun registerRoomActionReceiver() {
        val json = koin.get<NetworkJson>()
        val service = koin.get<MahjongTableRoomService>()
        MahjongChannels.roomAction.registerServerReceiver(json) { _, player, action ->
            service.handleRoomAction(player, action)
        }
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
     * 將玩家連線事件轉送給可測試的斷線政策執行器。
     */
    private fun registerPlayerConnectionEvents() {
        val lifecycleService = koin.get<PlayerConnectionLifecycleService>()
        val dataStatus = koin.get<MahjongDataStatus>()
        events.onPlayerJoin { player ->
            dataStatus.notifyOnJoin(player)
            if (dataStatus.isMahjongAvailable) lifecycleService.onConnected(player.uuid.toKotlinUuid())
        }
        events.onPlayerDisconnect { player ->
            val playerId = player.uuid.toKotlinUuid()
            C2SChannel.forgetPlayer(playerId)
            if (dataStatus.isMahjongAvailable) lifecycleService.onDisconnected(playerId)
        }
    }

    /**
     * 接收操作 HUD 的候選選擇，實際合法性仍由伺服器目前 prompt 與正式流程驗證。
     */
    private fun registerDecisionSelectionReceiver() {
        val json = koin.get<NetworkJson>()
        val service = koin.get<MahjongTableGameActionService>()
        MahjongChannels.decisionSelection.registerServerReceiver(json) { _, player, selection ->
            service.select(player, selection)
        }
    }

    /**
     * 接收客戶端加入世界後主動送出的快照補送請求，見 [MahjongChannels.requestSnapshot] KDoc。
     */
    private fun registerRequestSnapshotReceiver() {
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
     */
    private fun registerSetAutoSortHandReceiver() {
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
     */
    private fun registerAutomaticControlUpdateReceiver() {
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
