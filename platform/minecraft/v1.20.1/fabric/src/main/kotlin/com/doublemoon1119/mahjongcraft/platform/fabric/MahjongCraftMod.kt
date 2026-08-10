package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.registerBuiltInRuleConfigDtos
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.platform.fabric.concurrency.FabricAppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.di.MahjongCraftApp
import com.doublemoon1119.mahjongcraft.platform.fabric.metadata.FabricRuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.player.DisconnectedPlayerLifecycleService
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModBlocks
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.fabric.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
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
        // 必須先於 fabricPlatformModule 裡的 Json single 第一次被解析之前完成，見
        // FabricPlatformModule.kt 的說明。
        registerBuiltInRuleConfigDtos()

        val koin = startKoin<MahjongCraftApp>().koin

        ModItems.register()
        ModBlocks.register(koin.get<MahjongTableRoomService>())

        val serverHolder = koin.get<FabricServerHolder>()
        val appScope = koin.get<FabricAppCoroutineScope>()
        val stateCleaner = koin.get<ServerSessionStateCleaner>()
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            serverHolder.set(server)
            appScope.startSession()
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            appScope.cancel()
            runBlocking { stateCleaner.clear() }
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
        val scope = koin.get<AppCoroutineScope>()
        MahjongChannels.gameCommand.registerServerReceiver(json) { _, player, envelope ->
            scope.launch {
                koin.get<GameFlowCoordinator>().invoke(
                    gameId = Uuid.parse(envelope.gameId),
                    playerId = player.uuid.toKotlinUuid(),
                    command = envelope.command.toDomain(),
                )
            }
        }
    }
}
