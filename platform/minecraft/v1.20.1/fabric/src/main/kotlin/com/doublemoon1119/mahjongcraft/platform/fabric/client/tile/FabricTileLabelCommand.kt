package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigState
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.notification.FabricPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.koin.core.annotation.Single

/**
 * 純 client-only 指令 `/mahjongcraft_client label toggle`：切換牌面角落輔助標籤（給非中文圈玩家看的
 * 數字／字母，見 [TileLabelRegistry]）開關，
 * 純本機設定，不會送到伺服器（Fabric client command 在到達網路層之前就被攔截處理）。
 *
 * 根節點用共用的 [CLIENT_COMMAND_ROOT]，理由見該常數 KDoc。
 *
 * 這裡先只做「指令＋持久化」這一步：切換 [MahjongClientConfigStore] 裡的 [MahjongClientConfigState.tileLabelsEnabled]
 * 並立即寫回磁碟，還沒有渲染端消費這個開關去實際畫出標籤——那是下一步，等這個開關本身可以正常切換、
 * 持久化沒問題之後再接上去。
 */
@Single
class FabricTileLabelCommand(
    private val configStore: MahjongClientConfigStore,
) {
    /** 註冊指令；只能在 client entrypoint 呼叫。 */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal(CLIENT_COMMAND_ROOT).then(
                    ClientCommandManager.literal(LABEL_SUBCOMMAND).then(
                        ClientCommandManager.literal(TOGGLE_SUBCOMMAND).executes {
                            toggle()
                            COMMAND_SUCCESS
                        },
                    ),
                ),
            )
        }
    }

    /** 切換開關、立即持久化，並在本地聊天欄回饋「切換前狀態 → 切換後狀態」。 */
    private fun toggle() {
        val enabled = !configStore.current.tileLabelsEnabled
        configStore.setTileLabelsEnabled(enabled)
        MinecraftClient.getInstance().player?.sendMessage(tileLabelsToggledMessage(enabled), false)
    }

    /**
     * 組合切換訊息：前綴 + 切換前狀態 → 切換後狀態，「開啟」亮綠、「關閉」亮紅，
     * 比照 [FabricPlayerFeedbackPublisher] 的 `readyToggledMessage` 配色慣例。
     */
    private fun tileLabelsToggledMessage(enabled: Boolean): MutableText {
        val fromKey =
            if (enabled) MinecraftMessageKeys.TILE_LABELS_STATE_OFF else MinecraftMessageKeys.TILE_LABELS_STATE_ON
        val fromColor = if (enabled) Formatting.RED else Formatting.GREEN
        val toKey =
            if (enabled) MinecraftMessageKeys.TILE_LABELS_STATE_ON else MinecraftMessageKeys.TILE_LABELS_STATE_OFF
        val toColor = if (enabled) Formatting.GREEN else Formatting.RED

        return Text.translatable(MinecraftMessageKeys.TILE_LABELS_TOGGLE_PREFIX)
            .append(Text.translatable(fromKey).formatted(fromColor))
            .append(Text.literal(" → "))
            .append(Text.translatable(toKey).formatted(toColor))
    }

    private companion object {
        /** `label` 子指令節點。 */
        const val LABEL_SUBCOMMAND: String = "label"

        /** `toggle` 子指令節點。 */
        const val TOGGLE_SUBCOMMAND: String = "toggle"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1
    }
}
