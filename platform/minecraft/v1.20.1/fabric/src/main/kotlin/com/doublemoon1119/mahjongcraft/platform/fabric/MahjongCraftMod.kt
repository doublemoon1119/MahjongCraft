package com.doublemoon1119.mahjongcraft.platform.fabric

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.dto.registerBuiltInRuleConfigDtos
import com.doublemoon1119.mahjongcraft.flow.dto.toDomain
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.command.registerMahjongTestCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.di.MahjongCraftApp
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.koin.core.Koin
import org.koin.plugin.module.dsl.startKoin
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class MahjongCraftMod : ModInitializer {

    private val logger = LoggerFactory.getLogger("mahjongcraft")

    override fun onInitialize() {
        ModItems.register()

        // 必須先於 fabricPlatformModule 裡的 Json single 第一次被解析之前完成，見
        // FabricPlatformModule.kt 的說明。
        registerBuiltInRuleConfigDtos()

        val koin = startKoin<MahjongCraftApp>().koin

        val serverHolder = koin.get<FabricServerHolder>()
        ServerLifecycleEvents.SERVER_STARTED.register { server -> serverHolder.set(server) }
        ServerLifecycleEvents.SERVER_STOPPING.register { serverHolder.clear() }

        registerGameCommandReceiver(koin)
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> registerMahjongTestCommand(dispatcher, koin) }

        logger.info("MahjongCraft (Fabric, Minecraft 1.20.1) initialized.")
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
