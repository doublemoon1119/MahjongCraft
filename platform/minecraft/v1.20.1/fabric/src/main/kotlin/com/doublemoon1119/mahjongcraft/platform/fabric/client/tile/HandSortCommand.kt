package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferenceService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.notification.PlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutoSortHandPreferenceUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.configSaveFailureMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * `/mahjongcraft_client hand_sort toggle`：切換自動整理手牌開關。跟純 client-only 的
 * [TileLabelCommand] 不同，這個偏好還要送一份 C2S 封包給
 * 伺服器——手牌 tile entity 是伺服器端共用的實體，排序結果必須由伺服器套用才會反映在實際世界座標上，
 * 純本機開關做不到，見該 channel KDoc。
 *
 * 根節點用共用的 [CLIENT_COMMAND_ROOT]，理由見該常數 KDoc。
 */
class HandSortCommand(
    private val preferenceService: ClientAutoSortHandPreferenceService,
) : ClientCommand {
    /**
     * 註冊指令；只能在 client entrypoint 呼叫。
     *
     * @param events 登記用戶端指令的事件來源。
     */
    fun register(events: ClientEvents) {
        events.onRegisterClientCommand(this)
    }

    override fun <S : Any> build(): LiteralArgumentBuilder<S> = literal<S>(CLIENT_COMMAND_ROOT).then(
        literal<S>(HAND_SORT_SUBCOMMAND).then(
            literal<S>(TOGGLE_SUBCOMMAND).executes { toggle() },
        ),
    )

    /** 切換開關、立即持久化並同步給伺服器，並在本地聊天欄回饋「切換前狀態 → 切換後狀態」。 */
    private fun toggle(): Int = when (val result = preferenceService.toggle()) {
        is ClientAutoSortHandPreferenceUpdateResult.Updated -> {
            Minecraft.getInstance().player?.displayClientMessage(handSortToggledMessage(result.enabled), false)
            COMMAND_SUCCESS
        }

        is ClientAutoSortHandPreferenceUpdateResult.Unchanged -> COMMAND_SUCCESS
        is ClientAutoSortHandPreferenceUpdateResult.SaveFailed -> {
            Minecraft.getInstance().player?.displayClientMessage(
                configSaveFailureMessage(
                    settingName = Component.translatable(MinecraftClientConfigScreenKeys.AUTO_SORT_HAND),
                    details = result.failure.message,
                ),
                false,
            )
            COMMAND_FAILURE
        }

        is ClientAutoSortHandPreferenceUpdateResult.SyncFailed -> {
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable(MinecraftMessageKeys.AUTOMATIC_CONTROL_PREFERENCE_SYNC_FAILED),
                false,
            )
            COMMAND_FAILURE
        }
    }

    /**
     * 組合切換訊息：前綴 + 切換前狀態 → 切換後狀態，「開啟」亮綠、「關閉」亮紅，
     * 比照 [PlayerFeedbackPublisher] 的 `readyToggledMessage` 配色慣例。
     *
     * @param enabled 切換後是否為開啟狀態。
     */
    private fun handSortToggledMessage(enabled: Boolean): MutableComponent {
        val fromKey =
            if (enabled) MinecraftMessageKeys.HAND_SORT_STATE_OFF else MinecraftMessageKeys.HAND_SORT_STATE_ON
        val fromColor = if (enabled) ChatFormatting.RED else ChatFormatting.GREEN
        val toKey =
            if (enabled) MinecraftMessageKeys.HAND_SORT_STATE_ON else MinecraftMessageKeys.HAND_SORT_STATE_OFF
        val toColor = if (enabled) ChatFormatting.GREEN else ChatFormatting.RED

        return Component.translatable(MinecraftMessageKeys.HAND_SORT_TOGGLE_PREFIX)
            .append(Component.translatable(fromKey).withStyle(fromColor))
            .append(Component.literal(" → "))
            .append(Component.translatable(toKey).withStyle(toColor))
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
