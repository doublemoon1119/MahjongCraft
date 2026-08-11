package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.concurrency.FabricAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.di.MahjongCraftApp
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.fabric.game.FabricDecisionTimeoutScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.metadata.FabricRuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.persistence.FabricTableLocationPersistence
import com.doublemoon1119.mahjongcraft.platform.fabric.player.DisconnectedPlayerLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModBlocks
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.fabric.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.table.FabricTableLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.table.FabricTableLocationValidationService
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.koin.core.Koin
import org.koin.plugin.module.dsl.startKoin
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class MahjongCraftMod : ModInitializer {

    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    override fun onInitialize() {
        val koin = startKoin<MahjongCraftApp>().koin
        // Koin single 建立後，必須先於 Json 與遊戲流程服務第一次被解析前完成。
        FabricMahjongExtensions.initialize(
            moduleRegistry = koin.get<MahjongModuleRegistry>(),
            networkRegistries = koin.get<NetworkDtoRegistries>(),
            persistenceRegistries = koin.get<PersistenceRegistries>(),
        )

        ModItems.register()
        val tableLifecycleService = koin.get<FabricTableLifecycleService>()
        val tableLocationValidation = koin.get<FabricTableLocationValidationService>()
        ModBlocks.register(koin.get<MahjongTableRoomService>(), tableLifecycleService)
        tableLifecycleService.registerEvents()
        tableLocationValidation.registerEvents()
        koin.get<FabricDecisionTimeoutScheduler>().registerEvents()

        val serverHolder = koin.get<FabricServerHolder>()
        val appScope = koin.get<FabricAppCoroutineScope>()
        val stateCleaner = koin.get<ServerSessionStateCleaner>()
        val statePersistence = koin.get<FabricAuthoritativeStatePersistence>()
        val decisionTimerManager = koin.get<GameDecisionTimerManager>()
        val tableLocationPersistence = koin.get<FabricTableLocationPersistence>()
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            runBlocking { statePersistence.attach(server) }
            tableLocationPersistence.attach(server)
            tableLocationValidation.startSession(server)
            serverHolder.set(server)
            appScope.startSession()
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            appScope.cancel()
            tableLocationValidation.stopSession()
            tableLocationPersistence.detach()
            runBlocking {
                decisionTimerManager.settleAll()
                statePersistence.detach()
                stateCleaner.clear()
            }
            serverHolder.clear()
        }

        registerGameCommandReceiver(koin)
        registerPlayerConnectionEvents(koin)

        logger.info(koin.get<FabricRuntimeMetadata>().initializationMessage())
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
}
