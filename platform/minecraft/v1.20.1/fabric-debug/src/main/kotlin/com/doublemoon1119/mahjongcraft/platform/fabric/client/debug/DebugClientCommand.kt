package com.doublemoon1119.mahjongcraft.platform.fabric.client.debug

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.text.clientConfigEntries
import com.doublemoon1119.mahjongcraft.platform.fabric.text.configShowMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

/**
 * 客戶端的 debug 指令 `/mahjongcraft_client debug hovered_text client_config`：以正式設定訊息建構器顯示客戶端設定，
 * 檢查訊息懸停的內容。
 *
 * @property configStore 取得本機設定的來源；執行時才取，客戶端初始化順序不影響登記。
 */
class DebugClientCommand(private val configStore: () -> MahjongClientConfigStore) {
    /**
     * 建立指令樹；與本體的客戶端指令共用同一個根節點，由指令系統合併。
     *
     * @return 指令樹的根節點。
     */
    fun build(): LiteralArgumentBuilder<FabricClientCommandSource> = ClientCommandManager.literal(CLIENT_COMMAND_ROOT).then(
        ClientCommandManager.literal(DEBUG_SUBCOMMAND).then(
            ClientCommandManager.literal(HOVERED_TEXT_SUBCOMMAND).then(
                ClientCommandManager.literal(CLIENT_CONFIG_ARGUMENT).executes { context -> previewClientConfig(context.source) },
            ),
        ),
    )

    /**
     * 以正式設定訊息建構器發送客戶端設定懸停預覽。
     *
     * @param source 接收預覽的指令來源。
     * @return Brigadier 成功值。
     */
    private fun previewClientConfig(source: FabricClientCommandSource): Int {
        val store = configStore()
        source.sendFeedback(
            configShowMessage(
                configName = Component.translatable(MinecraftConfigCommandKeys.CLIENT_CONFIG),
                displayPath = store.displayPath,
                entries = clientConfigEntries(store.current),
            ),
        )
        return COMMAND_SUCCESS
    }

    /** 指令節點名稱與回傳值。 */
    internal companion object {
        /** 開發期診斷子指令。 */
        const val DEBUG_SUBCOMMAND: String = "debug"

        /** 可懸停正式訊息的診斷子指令。 */
        const val HOVERED_TEXT_SUBCOMMAND: String = "hovered_text"

        /** 客戶端設定的懸停預覽。 */
        const val CLIENT_CONFIG_ARGUMENT: String = "client_config"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1
    }
}
