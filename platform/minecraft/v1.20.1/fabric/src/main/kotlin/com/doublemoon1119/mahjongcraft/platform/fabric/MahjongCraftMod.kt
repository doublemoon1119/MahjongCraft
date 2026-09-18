package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.DeclareRiichiUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.HandSortPreferenceUpdateMode
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.SetHandSortPreferenceUseCase
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.platform.fabric.di.MahjongCraftClientApp
import com.doublemoon1119.mahjongcraft.platform.fabric.di.MahjongCraftServerApp
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.fabric.metadata.FabricRuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.network.C2SChannel
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModBlocks
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItemGroups
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModSounds
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TableOpeningPresentationOperationTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.event.TablePresentationBusyTracker
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricDecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricGameCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationEffectScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongTableGameActionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.FabricDebugCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.registerDebugScriptedAiStrategies
import com.doublemoon1119.mahjongcraft.platform.fabric.server.observer.FabricObserverSnapshotBroadcastService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricTableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.PlayerConnectionLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricMahjongLobbyInfoLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricRoomCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricTickMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import org.koin.core.Koin
import org.koin.plugin.module.dsl.startKoin
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class MahjongCraftMod : ModInitializer {

    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    override fun onInitialize() {
        val koin = startDependencyInjection()
        // Koin single 建立後，必須先於 Json 與遊戲流程服務第一次被解析前完成。
        FabricMahjongExtensions.initialize(
            coreRegistries = koin.get<CoreExtensionRegistries>(),
            presentationRegistries = koin.get<MinecraftPresentationRegistries>(),
            tablePropKindRegistry = koin.get<FabricTablePropKindRegistry>(),
            declareRiichiUseCase = koin.get<DeclareRiichiUseCase>(),
            debugWinRoundContinuationState = koin.get<DebugWinRoundContinuationState>(),
            minecraftEnvironment = koin.get<MinecraftEnvironment>(),
        )
        // 開發環境限定：debug 情境的對手使用腳本 AI，正式產物不註冊。
        if (koin.get<MinecraftEnvironment>().isDevelopment) {
            koin.get<MahjongAiStrategyRegistry>().registerDebugScriptedAiStrategies()
        }
        ModItems.register()
        ModSounds.register()
        ModEntities.register()
        val tableLifecycleService = koin.get<FabricTableLifecycleService>()
        val tableLocationValidation = koin.get<FabricTableLocationValidationService>()
        ModBlocks.register(koin.get<MahjongTableRoomService>(), tableLifecycleService)
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
        val appScope = koin.get<FabricAppCoroutineScope>()
        val stateCleaner = koin.get<ServerSessionStateCleaner>()
        val statePersistence = koin.get<FabricAuthoritativeStatePersistence>()
        val decisionTimerManager = koin.get<GameDecisionTimerManager>()
        val tableLocationPersistence = koin.get<FabricTableLocationPersistence>()
        val configManager = koin.get<FabricServerConfigManager>()
        val mahjongTileCollisionService = koin.get<MahjongTileCollisionService>()
        val openingPresentationOperations = koin.get<TableOpeningPresentationOperationTracker>()
        val presentationBusyTracker = koin.get<TablePresentationBusyTracker>()
        val observerBroadcast = koin.get<FabricObserverSnapshotBroadcastService>()
        mahjongTileCollisionService.registerEvents()
        observerBroadcast.registerEvents()
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            initializeServerConfig(configManager, mahjongTileCollisionService, server)
            runBlocking { statePersistence.attach(server) }
            tableLocationPersistence.attach(server)
            tableLocationValidation.startSession(server)
            serverHolder.set(server)
            appScope.startSession()
            observerBroadcast.startSession()
            lobbyInfoLifecycle.startSession()
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            tableLocationValidation.stopSession()
            tableLocationPersistence.detach()
            runBlocking {
                // 順序很重要：appScope.shutdown() 必須先跑完，才能保證後面的 settleAll／detach／
                // clear 執行時，不會有任何 Draw／討論結果之類還在等鎖、卡在中途的協程被攔腰砍斷——
                // 那次原本該發生的權威狀態變更會完全遺失、不留任何訊號，是曾經真的踩過的問題。
                // 比照 GameDecisionTimerManager.settleAll() 自己 KDoc 要求的「先停止新命令、再結算、
                // 最後才解除 persistence dirty listener」。
                appScope.shutdown()
                observerBroadcast.stopSession()
                lobbyInfoLifecycle.stopSession()
                presentationBusyTracker.clearAll()
                openingPresentationOperations.clearAll()
                decisionTimerManager.settleAll()
                statePersistence.detach()
                stateCleaner.clear()
            }
            configManager.detach()
            serverHolder.clear()
        }

        registerGameCommandReceiver(koin)
        registerRoomActionReceiver(koin)
        registerRequestSnapshotReceiver(koin)
        registerSetAutoSortHandReceiver(koin)
        registerPlayerConnectionEvents(koin)
        koin.get<FabricServerConfigCommand>().register()
        koin.get<FabricRoomCommand>().register()
        koin.get<FabricGameCommand>().register()
        koin.get<FabricDebugCommand>().register()

        logger.info(koin.get<FabricRuntimeMetadata>().initializationMessage())
    }

    /** 將 RoomScreen 的受控操作交給桌級房間服務執行。 */
    private fun registerRoomActionReceiver(koin: Koin) {
        val json = koin.get<Json>()
        val service = koin.get<MahjongTableRoomService>()
        MahjongChannels.roomAction.registerServerReceiver(json) { _, player, action ->
            service.handleRoomAction(player, action)
        }
    }

    /** 依目前 Fabric environment 啟動 dedicated-server 或 client/integrated-server graph。 */
    private fun startDependencyInjection(): Koin = if (FabricLoader.getInstance().environmentType == EnvType.CLIENT) {
        startKoin<MahjongCraftClientApp>().koin
    } else {
        startKoin<MahjongCraftServerApp>().koin
    }

    /** 載入 server TOML；失敗時 manager 會保留程式預設值並記錄詳細錯誤。 */
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

    /** 將 Fabric 玩家連線事件轉送給可測試的斷線政策執行器。 */
    private fun registerPlayerConnectionEvents(koin: Koin) {
        val lifecycleService = koin.get<PlayerConnectionLifecycleService>()
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            lifecycleService.onConnected(handler.player.uuid.toKotlinUuid())
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            lifecycleService.onDisconnected(handler.player.uuid.toKotlinUuid())
        }
    }

    /**
     * 接收端跑在網路執行緒（見 [C2SChannel]），
     * `registerServerReceiver` 已經把 [envelope] 解碼、丟回伺服器執行緒；這裡再用 [AppCoroutineScope]
     * 啟動協程呼叫 [GameFlowCoordinator]（`suspend` 函式），不阻塞伺服器主執行緒。玩家身分一律用
     * 連線本身的 [ServerPlayerEntity.getUuid]，不信任封包內容宣稱的身分。
     */
    private fun registerGameCommandReceiver(koin: Koin) {
        val json = koin.get<Json>()
        val networkRegistries = koin.get<NetworkDtoRegistries>()
        val scope = koin.get<AppCoroutineScope>()
        MahjongChannels.gameCommand.registerServerReceiver(json) { _, player, envelope ->
            scope.launch {
                koin.get<GameFlowCoordinator>().invoke(
                    gameId = Uuid.parse(envelope.gameId),
                    playerId = player.uuid.toKotlinUuid(),
                    command = envelope.command.toDomain(networkRegistries),
                )
            }
        }
    }

    /** 接收操作 HUD 的候選選擇，實際合法性仍由伺服器目前 prompt 與正式流程驗證。 */
    private fun registerDecisionSelectionReceiver(koin: Koin) {
        val json = koin.get<Json>()
        val service = koin.get<MahjongTableGameActionService>()
        MahjongChannels.decisionSelection.registerServerReceiver(json) { _, player, selection ->
            service.select(player, selection)
        }
    }

    /** 接收客戶端加入世界後主動送出的快照補送請求，見 [MahjongChannels.requestSnapshot] KDoc。 */
    private fun registerRequestSnapshotReceiver(koin: Koin) {
        val json = koin.get<Json>()
        val lifecycleService = koin.get<PlayerConnectionLifecycleService>()
        MahjongChannels.requestSnapshot.registerServerReceiver(json) { _, player, _ ->
            lifecycleService.onSnapshotRequested(player.uuid.toKotlinUuid())
        }
    }

    /** 接收客戶端切換「自動整理手牌」偏好的請求，見 [MahjongChannels.setAutoSortHand] KDoc。 */
    private fun registerSetAutoSortHandReceiver(koin: Koin) {
        val json = koin.get<Json>()
        val useCase = koin.get<SetHandSortPreferenceUseCase>()
        val scope = koin.get<AppCoroutineScope>()
        MahjongChannels.restoreAutoSortHand.registerServerReceiver(json) { _, player, enabled ->
            scope.launch {
                useCase(player.uuid.toKotlinUuid(), enabled, HandSortPreferenceUpdateMode.RESTORE)
            }
        }
        MahjongChannels.setAutoSortHand.registerServerReceiver(json) { _, player, enabled ->
            scope.launch {
                useCase(player.uuid.toKotlinUuid(), enabled, HandSortPreferenceUpdateMode.USER_CHANGE)
            }
        }
    }
}
