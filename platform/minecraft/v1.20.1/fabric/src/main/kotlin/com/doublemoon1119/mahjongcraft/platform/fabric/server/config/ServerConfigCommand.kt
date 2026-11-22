package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.configReloadFailureMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.prefixedConfigMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.serverConfigShowMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component

/**
 * 註冊限制管理員使用的 server config reload 與 show 指令。
 *
 * @property configManager 伺服器設定載入與 reload 管理器。
 * @property mahjongTileCollisionService 將有效碰撞設定套用至麻將牌的服務。
 * @property scope 執行設定 reload 的應用協程作用域。
 * @property dispatchers 提供伺服器主執行緒 dispatcher。
 */
class ServerConfigCommand(
    private val configManager: ServerConfigManager,
    private val mahjongTileCollisionService: MahjongTileCollisionService,
    private val scope: AppCoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    /** 記錄 config 指令執行者與結果。 */
    private val logger = mahjongCraftLogger(ServerConfigCommand::class)

    /**
     * 將 `/mahjongcraft config reload|show` 加入指令分派器。
     *
     * 權限限制掛在 `config` 子節點而非 `mahjongcraft` 根節點，讓其他不需要管理員權限的
     * `/mahjongcraft` 子指令（例如房間階段的玩家指令）可以共用同一個根節點註冊，不受這裡的權限
     * 要求影響。
     *
     * @param events 提供指令登記時機的伺服器事件來源。
     */
    fun register(events: ServerEvents) {
        events.onRegisterCommands { dispatcher ->
            dispatcher.register(
                literal(MinecraftModMetadata.MOD_ID)
                    .then(
                        literal("config")
                            .requires { source -> source.hasPermission(REQUIRED_PERMISSION_LEVEL) }
                            .then(literal("reload").executes { context -> reload(context.source) })
                            .then(literal("show").executes { context -> show(context.source) }),
                    ),
            )
        }
    }

    /**
     * 重新載入設定並向執行者回報結果。
     *
     * @param source 發起 reload 的管理員命令來源。
     * @return 表示已排程重新載入的成功結果碼，不代表設定已載入成功。
     */
    private fun reload(source: CommandSourceStack): Int {
        scope.launch {
            val result = configManager.reload()
            withContext(dispatchers.main) { reportReload(source, result) }
        }
        return COMMAND_SUCCESS
    }

    /**
     * 在主執行緒套用碰撞設定並回覆已完成的 reload。
     *
     * @param source 發起 reload 的管理員。
     * @param result 完整設定與記錄政策的更新結果。
     * @return Brigadier 結果碼。
     */
    private fun reportReload(source: CommandSourceStack, result: MinecraftServerConfigUpdateResult): Int = when (result) {
        is MinecraftServerConfigUpdateResult.Success -> {
            mahjongTileCollisionService.applyToLoaded(source.server, result.config)
            logger.info("Server config reloaded by {}", source.textName)
            source.sendSuccess(
                {
                    prefixedConfigMessage(
                        message = Component.translatable(
                            MinecraftConfigCommandKeys.RELOADED,
                            Component.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG),
                        ),
                        color = ChatFormatting.GREEN,
                    )
                },
                false,
            )
            COMMAND_SUCCESS
        }
        is MinecraftServerConfigUpdateResult.Failure -> {
            logger.warn("Server config reload requested by {} failed: {}", source.textName, result.message)
            source.sendFailure(
                configReloadFailureMessage(Component.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG), result.message),
            )
            COMMAND_FAILURE
        }
    }

    /**
     * 顯示目前生效的設定，以單則條列訊息提供各 TOML 分類的獨立懸停詳情。
     *
     * @param source 發起 show 的管理員命令來源。
     * @return 訊息送出後的成功結果碼。
     */
    private fun show(source: CommandSourceStack): Int {
        logger.debug(
            "Effective server config hover displayedTo={} path={} config={}",
            source.textName,
            configManager.displayPath,
            configManager.formattedCurrentToml(),
        )
        source.sendSuccess(
            {
                serverConfigShowMessage(configManager.displayPath, configManager.current)
            },
            false,
        )
        return COMMAND_SUCCESS
    }

    /** 指令權限與 Brigadier 回傳值。 */
    private companion object {
        /** Minecraft operator level 2。 */
        const val REQUIRED_PERMISSION_LEVEL: Int = 2

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
