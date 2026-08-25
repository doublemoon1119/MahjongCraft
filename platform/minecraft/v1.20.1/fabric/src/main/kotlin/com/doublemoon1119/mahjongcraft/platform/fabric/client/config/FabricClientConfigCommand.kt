package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.FabricTileLabelCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.text.configShowMessage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.prefixedConfigMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.util.Formatting
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

/**
 * 純 client-only 指令 `/mahjongcraft_client config reload|show`：跟 server 端
 * `/mahjongcraft config reload|show`（見 [FabricServerConfigCommand]）效果
 * 完全一致——`reload` 重新讀取玩家手動編輯過的 `client.toml`，`show` 把目前記憶體內實際生效的標準
 * TOML 收進單行可 hover 的中括號標籤裡；訊息格式（`[MahjongCraft] ...` 前綴、成功綠色／失敗紅色／
 * 標題青色／hover 內容依 section·key·value 上色）共用同一套 [prefixedConfigMessage]／
 * [bracketedInteractiveLabel]／[configShowHoverText]。跟 server 版本的差別只有一點：這裡是玩家自己
 * 本機的設定檔，沒有權限限制，任何人都能執行。
 *
 * 根節點用共用的 [CLIENT_COMMAND_ROOT]，理由見該常數 KDoc；`config` 子節點在這裡跟 [FabricTileLabelCommand] 的 `label`
 * 子節點是各自獨立註冊、共用同一個根節點，跟 server 端 `FabricRoomCommand`／`FabricServerConfigCommand`
 * 共用 `mahjongcraft` 根節點的既有慣例一致。
 */
@Single
class FabricClientConfigCommand(
    private val configStore: MahjongClientConfigStore,
    private val minecraftEnvironment: MinecraftEnvironment,
) {
    /** 記錄 config 指令執行結果，對稱 server 端 `FabricServerConfigCommand` 的 logger。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 註冊指令；只能在 client entrypoint 呼叫。 */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val root = ClientCommandManager.literal(CLIENT_COMMAND_ROOT).then(
                ClientCommandManager.literal(CONFIG_SUBCOMMAND)
                    .then(
                        ClientCommandManager.literal(RELOAD_SUBCOMMAND)
                            .executes { context -> reload(context.source) },
                    )
                    .then(
                        ClientCommandManager.literal(SHOW_SUBCOMMAND).executes { context -> show(context.source) },
                    ),
            )
            if (minecraftEnvironment.isDevelopment) {
                root.then(
                    ClientCommandManager.literal(DEBUG_SUBCOMMAND).then(
                        ClientCommandManager.literal(HOVERED_TEXT_SUBCOMMAND).then(
                            ClientCommandManager.literal(CLIENT_CONFIG_ARGUMENT)
                                .executes { context -> previewHoveredText(context.source) },
                        ),
                    ),
                )
            }
            dispatcher.register(root)
        }
    }

    /** 以正式 config builder 發送 client config hovered-text 預覽。 */
    private fun previewHoveredText(source: FabricClientCommandSource): Int {
        source.sendFeedback(configShowMessage("Effective client config ", configStore.displayPath, configStore.formattedCurrentToml()))
        return COMMAND_SUCCESS
    }

    /** 重新載入設定並向執行者回報結果。 */
    private fun reload(source: FabricClientCommandSource): Int = when (val result = configStore.load()) {
        is MahjongClientConfigUpdateResult.Success -> {
            logger.info("Client config reloaded")
            source.sendFeedback(prefixedConfigMessage("Client config reloaded", Formatting.GREEN))
            COMMAND_SUCCESS
        }

        is MahjongClientConfigUpdateResult.Failure -> {
            logger.warn("Client config reload failed: {}", result.message)
            source.sendError(prefixedConfigMessage(result.message, Formatting.RED))
            COMMAND_FAILURE
        }
    }

    /**
     * 顯示目前記憶體內實際生效的標準 TOML；收進單行可 hover 的中括號標籤裡（而不是直接把每一行都貼進
     * 聊天欄），理由見 [configShowHoverText] KDoc。
     */
    private fun show(source: FabricClientCommandSource): Int {
        logger.debug(
            "Effective client config hover path={} config={}",
            configStore.displayPath,
            configStore.formattedCurrentToml(),
        )
        source.sendFeedback(
            configShowMessage("Effective client config ", configStore.displayPath, configStore.formattedCurrentToml()),
        )
        return COMMAND_SUCCESS
    }

    private companion object {
        /** `config` 子指令節點。 */
        const val CONFIG_SUBCOMMAND: String = "config"

        /** `reload` 子指令節點。 */
        const val RELOAD_SUBCOMMAND: String = "reload"

        /** `show` 子指令節點。 */
        const val SHOW_SUBCOMMAND: String = "show"

        /** 開發期診斷子指令。 */
        const val DEBUG_SUBCOMMAND: String = "debug"

        /** 可懸停正式訊息的診斷子指令。 */
        const val HOVERED_TEXT_SUBCOMMAND: String = "hovered_text"

        /** Client config 正式 hover 預覽。 */
        const val CLIENT_CONFIG_ARGUMENT: String = "client_config"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
