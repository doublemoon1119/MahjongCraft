package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.di.MahjongCraftClientApp
import com.doublemoon1119.mahjongcraft.platform.fabric.di.MahjongCraftServerApp
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.fabric.metadata.FabricRuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModBlocks
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItemGroups
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency.FabricAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricDecisionTimerScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricGameCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricTableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.server.player.DisconnectedPlayerLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricRoomCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.time.FabricTickMonotonicClock
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
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
            moduleRegistry = koin.get<MahjongModuleRegistry>(),
            tileTypeRegistry = koin.get<TileTypeRegistry>(),
            networkRegistries = koin.get<NetworkDtoRegistries>(),
            persistenceRegistries = koin.get<PersistenceRegistries>(),
            minecraftTileAssetRegistry = koin.get<MinecraftTileAssetRegistry>(),
            aiStrategyDisplayNameRegistry = koin.get<AiStrategyDisplayNameRegistry>(),
            tileDisplayNameRegistry = koin.get<TileDisplayNameRegistry>(),
            ruleModuleDisplayNameRegistry = koin.get<RuleModuleDisplayNameRegistry>(),
            tileEmojiRegistry = koin.get<TileEmojiRegistry>(),
            tileLabelRegistry = koin.get<TileLabelRegistry>(),
        )
        ModItems.register()
        ModEntities.register()
        val tableLifecycleService = koin.get<FabricTableLifecycleService>()
        val tableLocationValidation = koin.get<FabricTableLocationValidationService>()
        ModBlocks.register(koin.get<MahjongTableRoomService>(), tableLifecycleService)
        ModItemGroups.register()
        tableLifecycleService.registerEvents()
        tableLocationValidation.registerEvents()
        koin.get<FabricDecisionTimerScheduler>().registerEvents()
        koin.get<FabricTickMonotonicClock>().registerEvents()

        val serverHolder = koin.get<FabricServerHolder>()
        val appScope = koin.get<FabricAppCoroutineScope>()
        val stateCleaner = koin.get<ServerSessionStateCleaner>()
        val statePersistence = koin.get<FabricAuthoritativeStatePersistence>()
        val decisionTimerManager = koin.get<GameDecisionTimerManager>()
        val tableLocationPersistence = koin.get<FabricTableLocationPersistence>()
        val configManager = koin.get<FabricServerConfigManager>()
        val mahjongTileCollisionService = koin.get<MahjongTileCollisionService>()
        mahjongTileCollisionService.registerEvents()
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            initializeServerConfig(configManager, mahjongTileCollisionService, server)
            runBlocking { statePersistence.attach(server) }
            tableLocationPersistence.attach(server)
            tableLocationValidation.startSession(server)
            serverHolder.set(server)
            appScope.startSession()
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
                decisionTimerManager.settleAll()
                statePersistence.detach()
                stateCleaner.clear()
            }
            configManager.detach()
            serverHolder.clear()
        }

        registerGameCommandReceiver(koin)
        registerUpdateGameConfigReceiver(koin)
        registerPlayerConnectionEvents(koin)
        koin.get<FabricServerConfigCommand>().register()
        koin.get<FabricRoomCommand>().register()
        koin.get<FabricGameCommand>().register()

        logger.info(koin.get<FabricRuntimeMetadata>().initializationMessage())
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
        server: net.minecraft.server.MinecraftServer,
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
        val lifecycleService = koin.get<DisconnectedPlayerLifecycleService>()
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            lifecycleService.onConnected(handler.player.uuid.toKotlinUuid())
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            lifecycleService.onDisconnected(handler.player.uuid.toKotlinUuid())
        }
    }

    /**
     * 接收端跑在網路執行緒（見 [com.doublemoon1119.mahjongcraft.platform.fabric.network.C2SChannel]），
     * `registerServerReceiver` 已經把 [envelope] 解碼、丟回伺服器執行緒；這裡再用 [AppCoroutineScope]
     * 啟動協程呼叫 [GameFlowCoordinator]（`suspend` 函式），不阻塞伺服器主執行緒。玩家身分一律用
     * 連線本身的 [net.minecraft.server.network.ServerPlayerEntity.getUuid]，不信任封包內容宣稱的身分。
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

    /**
     * 接收設定編輯畫面送出的原始 JSON 字串，直接轉呼叫既有的 [MahjongTableRoomService.updateConfig]
     * （內部已自行 launch 協程，這裡不需要額外包一層）。
     */
    private fun registerUpdateGameConfigReceiver(koin: Koin) {
        val json = koin.get<Json>()
        MahjongChannels.updateGameConfig.registerServerReceiver(json) { _, player, configJson ->
            koin.get<MahjongTableRoomService>().updateConfig(player, configJson)
        }
    }
}
