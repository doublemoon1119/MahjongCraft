package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PlayerPortraitRenderer
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PublicPlayerIndicatorTextResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.notification.FabricPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.toKotlinUuid

/**
 * 純 client-only 指令 `/mahjongcraft_client room_config screen`：開啟玩家目前所在房間的
 * [RoomScreen] 設定頁；由 [FabricPlayerFeedbackPublisher] 印出的「所在麻將遊戲的規則」訊息點擊觸發，
 * 玩家也可以自己手動輸入（Fabric client command 在到達網路層之前就被攔截處理，不會送到伺服器）。
 *
 * 只會開啟玩家自己實際身處（[ClientMahjongStateStore.findTableWhereSeated]）的那間房間，不接受指定
 * 其他桌子——不管是點聊天訊息還是手動輸入都一樣。一般成員會開啟唯讀設定，只有房主能編輯；伺服器端
 * `UpdateConfigUseCase` 的房主驗證仍是唯一權威判斷。
 *
 * 指令本身只設定 [pendingOpen]，真正的 [openScreen] 呼叫延後到下一次 [ClientTickEvents.END_CLIENT_TICK]
 * 才執行：在聊天室手動輸入指令按下 Enter 時，vanilla `ChatScreen.keyPressed()` 會在指令執行完「之後」
 * 、在同一次呼叫內無條件再呼叫一次 `client.setScreen(null)` 關閉聊天視窗，如果 `setScreen(RoomScreen)`
 * 跟指令本身同步發生在那一次呼叫裡，會被這個收尾動作立刻蓋掉（已在遊戲內實測確認）。`MinecraftClient`
 * 是 `ReentrantThreadExecutor`，`execute { ... }` 只有在呼叫端不在 client 執行緒上、或目前正在跑佇列
 * 任務時才會真的排入佇列，從指令 callback（本來就在 client 執行緒、且不是佇列任務）呼叫只會原地同步
 * 執行，並不會真的延後——必須改用 tick 事件才能確保排到 `keyPressed()` 整個呼叫鏈結束之後。點擊聊天
 * 訊息裡的連結不會觸發 `ChatScreen` 這段收尾邏輯，本來就不受影響。
 *
 * 根節點用共用的 [CLIENT_COMMAND_ROOT]，理由見該常數 KDoc。
 */
@Single
class FabricRoomConfigScreenCommand(
    private val stateStore: ClientMahjongStateStore,
    @Provided private val configPresentations: GameConfigPresentationRegistry,
    @Provided private val configResolver: GameConfigPresentationResolver,
    @Provided private val ruleNames: RuleModuleDisplayNameRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
    @Provided private val aiStrategies: MahjongAiStrategyRegistry,
    @Provided private val aiStrategyNames: AiStrategyDisplayNameRegistry,
    @Provided private val appearanceSources: RoomMemberAppearanceSourceRegistry,
    private val indicatorTextResolver: PublicPlayerIndicatorTextResolver,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
    private val profileResolver: ClientPlayerProfileResolver,
) {
    private var pendingOpen = false

    /** 註冊指令；只能在 client entrypoint 呼叫。 */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal(CLIENT_COMMAND_ROOT).then(
                    ClientCommandManager.literal(ROOM_CONFIG_SUBCOMMAND).then(
                        ClientCommandManager.literal(SCREEN_SUBCOMMAND).executes {
                            pendingOpen = true
                            COMMAND_SUCCESS
                        },
                    ),
                ),
            )
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (pendingOpen) {
                pendingOpen = false
                openScreen()
            }
        }
    }

    /** 資格檢查通過才開畫面，否則在本地顯示原因（純本機顯示，不送到伺服器）。 */
    private fun openScreen() {
        val client = MinecraftClient.getInstance()
        val localPlayerId = client.player?.uuid?.toKotlinUuid()
        val tableId = localPlayerId?.let(stateStore::findTableWhereSeated)
        val snapshot = tableId?.let(stateStore::roomSnapshot)
        if (tableId == null || snapshot == null || !snapshot.isInRoom) {
            client.inGameHud.setOverlayMessage(Text.translatable(MinecraftMessageKeys.PLAYER_NOT_IN_ANY_GAME), false)
            return
        }
        client.setScreen(
            RoomScreen(
                stateStore = stateStore,
                tableId = tableId,
                configPresentations = configPresentations,
                configResolver = configResolver,
                ruleNames = ruleNames,
                portraitRenderer = portraitRenderer,
                aiStrategies = aiStrategies,
                aiStrategyNames = aiStrategyNames,
                appearanceSources = appearanceSources,
                indicatorTextResolver = indicatorTextResolver,
                json = json,
                networkRegistries = networkRegistries,
                profileResolver = profileResolver,
                openSettings = true,
            ),
        )
    }

    private companion object {
        /** `room_config` 子指令節點。 */
        const val ROOM_CONFIG_SUBCOMMAND: String = "room_config"

        /** 開啟畫面的子指令，跟 [com.doublemoon1119.mahjongcraft.platform.fabric.client.config.FabricClientConfigCommand] 的 `screen` 子節點用途一致。 */
        const val SCREEN_SUBCOMMAND: String = "screen"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1
    }
}
