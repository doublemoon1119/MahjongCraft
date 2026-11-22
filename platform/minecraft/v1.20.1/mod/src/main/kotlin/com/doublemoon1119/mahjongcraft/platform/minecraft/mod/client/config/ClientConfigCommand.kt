package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.command.ClientCommandFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.TileLabelCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.clientConfigEntries
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.configReloadFailureMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.configShowMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.prefixedConfigMessage
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * 純 client-only 指令 `/mahjongcraft_client config reload|show|screen`：跟 server 端
 * `/mahjongcraft config reload|show`（見 [ServerConfigCommand]）效果
 * 完全一致——`reload` 重新讀取玩家手動編輯過的 `client.toml`，`show` 以 Client Config Screen 共用的
 * 本地化欄位和值顯示目前有效設定。跟 server 版本的差別只有一點：這裡是玩家自己的本機設定檔，沒有
 * 權限限制，任何人都能執行。
 *
 * 根節點用共用的 [CLIENT_COMMAND_ROOT]，理由見該常數 KDoc；`config` 子節點在這裡跟 [TileLabelCommand] 的 `label`
 * 子節點是各自獨立註冊、共用同一個根節點，跟 server 端 `RoomCommand`／`ServerConfigCommand`
 * 共用 `mahjongcraft` 根節點的既有慣例一致。
 *
 * @property configStore 本機設定的讀取與保存來源。
 * @property screenController 開啟設定畫面的入口。
 */
class ClientConfigCommand(
    private val configStore: MahjongClientConfigStore,
    private val screenController: MahjongClientConfigScreenController,
) : ClientCommand {
    /** 記錄 config 指令執行結果，對稱 server 端 `ServerConfigCommand` 的 logger。 */
    private val logger = mahjongCraftLogger(ClientConfigCommand::class)

    /**
     * 註冊指令；只能在 client entrypoint 呼叫。
     *
     * @param events 登記用戶端指令的事件來源。
     */
    fun register(events: ClientEvents) {
        events.onRegisterClientCommand(this)
    }

    override fun <S : Any> build(): LiteralArgumentBuilder<S> = literal<S>(CLIENT_COMMAND_ROOT).then(
        literal<S>(CONFIG_SUBCOMMAND)
            .then(
                literal<S>(RELOAD_SUBCOMMAND).executes { reload() },
            )
            .then(
                literal<S>(SHOW_SUBCOMMAND).executes { show() },
            ).then(
                literal<S>(SCREEN_SUBCOMMAND).executes {
                    screenController.openFromCommand()
                    COMMAND_SUCCESS
                },
            ),
    )

    /**
     * 重新載入設定並向執行者回報結果。
     *
     * @return 載入成功時為成功值，失敗時為失敗值。
     */
    private fun reload(): Int = when (val result = configStore.load()) {
        is MahjongClientConfigUpdateResult.Success -> {
            logger.info("Client config reloaded")
            ClientCommandFeedback.send(
                message = prefixedConfigMessage(
                    message = Component.translatable(
                        MinecraftConfigCommandKeys.RELOADED,
                        Component.translatable(MinecraftConfigCommandKeys.CLIENT_CONFIG),
                    ),
                    color = ChatFormatting.GREEN,
                ),
            )
            COMMAND_SUCCESS
        }

        is MahjongClientConfigUpdateResult.Failure -> {
            logger.warn("Client config reload failed: {}", result.message)
            ClientCommandFeedback.error(
                message = configReloadFailureMessage(Component.translatable(MinecraftConfigCommandKeys.CLIENT_CONFIG), result.message),
            )
            COMMAND_FAILURE
        }
    }

    /**
     * 顯示目前記憶體內實際生效的本地化設定欄位；完整內容收進單行可 hover 的詳情標籤。
     *
     * @return Brigadier 成功值。
     */
    private fun show(): Int {
        logger.debug(
            "Effective client config hover path={} config={}",
            configStore.displayPath,
            configStore.formattedCurrentToml(),
        )
        ClientCommandFeedback.send(
            message = clientConfigShowMessage(),
        )
        return COMMAND_SUCCESS
    }

    /** 使用 Client Config Screen 共用欄位建立目前設定訊息。 */
    private fun clientConfigShowMessage() = configShowMessage(
        configName = Component.translatable(MinecraftConfigCommandKeys.CLIENT_CONFIG),
        displayPath = configStore.displayPath,
        entries = clientConfigEntries(configStore.current),
    )

    private companion object {
        /** `config` 子指令節點。 */
        const val CONFIG_SUBCOMMAND: String = "config"

        /** `reload` 子指令節點。 */
        const val RELOAD_SUBCOMMAND: String = "reload"

        /** `show` 子指令節點。 */
        const val SHOW_SUBCOMMAND: String = "show"

        /** `screen` 子指令節點。 */
        const val SCREEN_SUBCOMMAND: String = "screen"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
