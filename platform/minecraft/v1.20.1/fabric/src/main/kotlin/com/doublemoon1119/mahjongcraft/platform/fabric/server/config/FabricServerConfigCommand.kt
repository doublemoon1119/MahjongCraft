package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.fabric.text.configReloadFailureMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.configShowMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.prefixedConfigMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.serverConfigEntries
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

/** 註冊限制管理員使用的 server config reload 與 show 指令。 */
@Single
class FabricServerConfigCommand(
    private val configManager: FabricServerConfigManager,
    private val mahjongTileCollisionService: MahjongTileCollisionService,
) {
    /** 記錄 config 指令執行者與結果。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 將 `/mahjongcraft config reload|show` 加入 Fabric command dispatcher。
     *
     * 權限限制掛在 `config` 子節點而非 `mahjongcraft` 根節點，讓其他不需要管理員權限的
     * `/mahjongcraft` 子指令（例如房間階段的玩家指令）可以共用同一個根節點註冊，不受這裡的權限
     * 要求影響。
     */
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal(MinecraftModMetadata.MOD_ID)
                    .then(
                        literal("config")
                            .requires { source -> source.hasPermissionLevel(REQUIRED_PERMISSION_LEVEL) }
                            .then(literal("reload").executes { context -> reload(context.source) })
                            .then(literal("show").executes { context -> show(context.source) }),
                    ),
            )
        }
    }

    /** 重新載入設定並向執行者回報結果。 */
    private fun reload(source: ServerCommandSource): Int = when (val result = configManager.reload()) {
        is MinecraftServerConfigUpdateResult.Success -> {
            mahjongTileCollisionService.applyToLoaded(source.server, result.config)
            logger.info("Server config reloaded by {}", source.name)
            source.sendFeedback(
                {
                    prefixedConfigMessage(
                        Text.translatable(
                            MinecraftConfigCommandKeys.RELOADED,
                            Text.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG),
                        ),
                        Formatting.GREEN,
                    )
                },
                false,
            )
            COMMAND_SUCCESS
        }
        is MinecraftServerConfigUpdateResult.Failure -> {
            logger.warn("Server config reload requested by {} failed: {}", source.name, result.message)
            source.sendError(
                configReloadFailureMessage(Text.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG), result.message),
            )
            COMMAND_FAILURE
        }
    }

    /**
     * 顯示目前記憶體內實際生效的本地化設定欄位；完整內容收進單行可 hover 的詳情標籤。
     */
    private fun show(source: ServerCommandSource): Int {
        logger.debug(
            "Effective server config hover displayedTo={} path={} config={}",
            source.name,
            configManager.displayPath,
            configManager.formattedCurrentToml(),
        )
        source.sendFeedback(
            {
                configShowMessage(
                    Text.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG),
                    configManager.displayPath,
                    serverConfigEntries(configManager.current),
                )
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
