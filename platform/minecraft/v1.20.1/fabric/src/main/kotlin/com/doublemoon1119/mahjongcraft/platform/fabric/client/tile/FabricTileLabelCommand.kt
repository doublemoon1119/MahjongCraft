package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import org.koin.core.annotation.Single

/**
 * 純 client-only 指令 `/mahjongcraft_client label toggle`：切換牌面角落輔助標籤（給非中文圈玩家看的
 * 數字／字母，見 [com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry]）開關，
 * 純本機設定，不會送到伺服器（Fabric client command 在到達網路層之前就被攔截處理）。
 *
 * 根節點刻意不用 [com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata.MOD_ID]
 * （`mahjongcraft`）——那個字面值已經被伺服器端的 `/mahjongcraft room|game|config`（見
 * [com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricRoomCommand] 等）用掉，client
 * 指令樹跟伺服器同步過來的指令樹合併時，同名根節點會被伺服器那棵樹蓋掉，導致這裡的 `label` 子節點在
 * Tab 補全跟指令說明裡完全消失（遊戲內驗證過的現象）。純 client-only、玩家會手動輸入的指令都應該掛在
 * 這個獨立的 `mahjongcraft_client` 根節點下面，避免重蹈覆轍；跟 [FabricOpenRoomConfigScreenCommand][
 * com.doublemoon1119.mahjongcraft.platform.fabric.client.room.FabricOpenRoomConfigScreenCommand] 那種
 * 純內部觸發、刻意不給玩家看到的指令（掛在 `🀇` 底下）用途不同，不能互相取代。
 *
 * 這裡先只做「指令＋持久化」這一步：切換 [MahjongClientConfigStore] 裡的
 * [com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigState.tileLabelsEnabled]
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

    /** 切換開關、立即持久化，並在本地聊天欄回饋切換後的狀態。 */
    private fun toggle() {
        val enabled = !configStore.current.tileLabelsEnabled
        configStore.setTileLabelsEnabled(enabled)
        val messageKey = if (enabled) MinecraftMessageKeys.TILE_LABELS_ENABLED else MinecraftMessageKeys.TILE_LABELS_DISABLED
        MinecraftClient.getInstance().player?.sendMessage(Text.translatable(messageKey), false)
    }

    private companion object {
        /** 純 client-only、玩家會手動輸入的指令共用根節點；理由見類別 KDoc。 */
        const val CLIENT_COMMAND_ROOT: String = "mahjongcraft_client"

        /** `label` 子指令節點。 */
        const val LABEL_SUBCOMMAND: String = "label"

        /** `toggle` 子指令節點。 */
        const val TOGGLE_SUBCOMMAND: String = "toggle"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1
    }
}
