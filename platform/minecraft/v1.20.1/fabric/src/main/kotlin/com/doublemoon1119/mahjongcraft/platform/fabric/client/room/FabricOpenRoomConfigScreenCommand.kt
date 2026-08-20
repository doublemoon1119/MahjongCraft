package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tile.FabricTileLabelCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.notification.FabricPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * 純 client-only 指令 `/🀇 open_room_config_screen`：由 [FabricPlayerFeedbackPublisher]
 * 印出的「所在麻將遊戲的規則」訊息點擊觸發，不會送到伺服器（Fabric client command 在到達網路層之前就
 * 被攔截處理），純粹當開啟 [GameConfigScreen] 的觸發器，不是給玩家手動輸入。
 *
 * 類別與指令名稱刻意明確標成「room config」（房間規則設定），不是單純的「config screen」——之後如果要
 * 加入 client 端自己的設定畫面（例如牌面輔助標籤的圖形化開關，見 [FabricTileLabelCommand]），
 * 「config screen」這種泛稱會分不清楚指的是房間規則設定還是 client 本機設定，所以先把這個既有的畫面正名。
 *
 * 根節點刻意用麻將牌字元 `🀇`（一萬）而非英文字串：本 mod 之後所有 client-only／內部觸發用指令都應該
 * 統一掛在這個根節點下面，玩家打 `/` 開頭的一般指令時字面比對不到，不會出現在即時建議清單裡；這個字元
 * 本身只當 Brigadier 指令字面值使用，不會被渲染成聊天文字，因此跟未來把麻將字元透過自訂字型換成牌面
 * 材質這件事無關，不會互相干擾。
 *
 * 開畫面前先用客戶端本地已經同步好的 [ClientMahjongStateStore.roomSnapshot] 做資格檢查（是否在房間內、
 * 是否為房主），避免非房主也能打開編輯畫面；這只是提升體驗用的前置檢查，伺服器端
 * `UpdateConfigUseCase` 原本的房主驗證依然是唯一的權威判斷。
 */
@Single
class FabricOpenRoomConfigScreenCommand(
    private val stateStore: ClientMahjongStateStore,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) {
    /** 註冊指令；只能在 client entrypoint 呼叫。 */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal(INTERNAL_COMMAND_ROOT).then(
                    ClientCommandManager.literal(OPEN_ROOM_CONFIG_SCREEN_SUBCOMMAND).executes {
                        openScreen()
                        COMMAND_SUCCESS
                    },
                ),
            )
        }
    }

    /** 資格檢查通過才開畫面，否則在本地顯示原因（純本機顯示，不送到伺服器）。 */
    private fun openScreen() {
        val client = MinecraftClient.getInstance()
        val snapshot = stateStore.roomSnapshot
        if (snapshot == null || !snapshot.isInRoom) {
            client.inGameHud.setOverlayMessage(Text.translatable(MinecraftMessageKeys.PLAYER_NOT_IN_GAME), false)
            return
        }
        if (!snapshot.isHost) {
            client.inGameHud.setOverlayMessage(Text.translatable(MinecraftMessageKeys.NOT_GAME_HOST), false)
            return
        }
        val configJson = json.encodeToString(snapshot.gameConfig.toDto(networkRegistries))
        client.setScreen(GameConfigScreen(configJson, json))
    }

    private companion object {
        /** 見類別 KDoc：所有 client-only／內部觸發用指令共用的根節點。 */
        const val INTERNAL_COMMAND_ROOT: String = "🀇"

        /** 開啟房間規則設定編輯畫面的子指令。 */
        const val OPEN_ROOM_CONFIG_SCREEN_SUBCOMMAND: String = "open_room_config_screen"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1
    }
}
