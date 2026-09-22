package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferenceService
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferenceUpdateResult
import com.doublemoon1119.mahjongcraft.platform.fabric.server.notification.FabricPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.fabric.text.configSaveFailureMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.koin.core.annotation.Single

/**
 * `/mahjongcraft_client hand_sort toggle`：切換自動整理手牌開關。跟純 client-only 的
 * [FabricTileLabelCommand] 不同，這個偏好還要送一份 C2S 封包給
 * 伺服器——手牌 tile entity 是伺服器端共用的實體，排序結果必須由伺服器套用才會反映在實際世界座標上，
 * 純本機開關做不到，見該 channel KDoc。
 *
 * 根節點用共用的 [CLIENT_COMMAND_ROOT]，理由見該常數 KDoc。
 */
@Single
class FabricHandSortCommand(
    private val preferenceService: ClientAutoSortHandPreferenceService,
) {
    /** 註冊指令；只能在 client entrypoint 呼叫。 */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal(CLIENT_COMMAND_ROOT).then(
                    ClientCommandManager.literal(HAND_SORT_SUBCOMMAND).then(
                        ClientCommandManager.literal(TOGGLE_SUBCOMMAND).executes { toggle() },
                    ),
                ),
            )
        }
    }

    /** 切換開關、立即持久化並同步給伺服器，並在本地聊天欄回饋「切換前狀態 → 切換後狀態」。 */
    private fun toggle(): Int = when (val result = preferenceService.toggle()) {
        is ClientAutoSortHandPreferenceUpdateResult.Updated -> {
            MinecraftClient.getInstance().player?.sendMessage(handSortToggledMessage(result.enabled), false)
            COMMAND_SUCCESS
        }

        is ClientAutoSortHandPreferenceUpdateResult.Unchanged -> COMMAND_SUCCESS
        is ClientAutoSortHandPreferenceUpdateResult.SaveFailed -> {
            MinecraftClient.getInstance().player?.sendMessage(
                configSaveFailureMessage(
                    Text.translatable(MinecraftClientConfigScreenKeys.AUTO_SORT_HAND),
                    result.failure.message,
                ),
                false,
            )
            COMMAND_FAILURE
        }

        is ClientAutoSortHandPreferenceUpdateResult.SyncFailed -> {
            MinecraftClient.getInstance().player?.sendMessage(
                Text.translatable(MinecraftMessageKeys.AUTOMATIC_CONTROL_PREFERENCE_SYNC_FAILED),
                false,
            )
            COMMAND_FAILURE
        }
    }

    /**
     * 組合切換訊息：前綴 + 切換前狀態 → 切換後狀態，「開啟」亮綠、「關閉」亮紅，
     * 比照 [FabricPlayerFeedbackPublisher] 的 `readyToggledMessage` 配色慣例。
     */
    private fun handSortToggledMessage(enabled: Boolean): MutableText {
        val fromKey =
            if (enabled) MinecraftMessageKeys.HAND_SORT_STATE_OFF else MinecraftMessageKeys.HAND_SORT_STATE_ON
        val fromColor = if (enabled) Formatting.RED else Formatting.GREEN
        val toKey =
            if (enabled) MinecraftMessageKeys.HAND_SORT_STATE_ON else MinecraftMessageKeys.HAND_SORT_STATE_OFF
        val toColor = if (enabled) Formatting.GREEN else Formatting.RED

        return Text.translatable(MinecraftMessageKeys.HAND_SORT_TOGGLE_PREFIX)
            .append(Text.translatable(fromKey).formatted(fromColor))
            .append(Text.literal(" → "))
            .append(Text.translatable(toKey).formatted(toColor))
    }

    private companion object {
        /** `hand_sort` 子指令節點。 */
        const val HAND_SORT_SUBCOMMAND: String = "hand_sort"

        /** `toggle` 子指令節點。 */
        const val TOGGLE_SUBCOMMAND: String = "toggle"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
