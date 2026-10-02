package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlDisplayResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferenceService
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlUpdateCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryScreenController
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.koin.core.annotation.Single
import org.lwjgl.glfw.GLFW

/**
 * 集中註冊並處理 Client Config Screen 的快捷鍵與程式化開啟入口。
 *
 * @property configStore 本機客戶端設定儲存區。
 * @property automaticCoordinator 本局自動操作更新協調器。
 * @property displayResolver 自動操作顯示解析器。
 * @property preferenceService 手牌偏好保存服務。
 * @property historyScreenController 對局歷史畫面控制器。
 */
@Single
class MahjongClientConfigScreenController(
    private val configStore: MahjongClientConfigStore,
    private val automaticCoordinator: ClientAutomaticControlUpdateCoordinator,
    private val displayResolver: AutomaticControlDisplayResolver,
    private val preferenceService: ClientAutoSortHandPreferenceService,
    private val historyScreenController: HistoryScreenController,
) {
    /** 預設以分號開啟 Client Config Screen 的按鍵綁定。 */
    private lateinit var openKeyBinding: KeyBinding

    /** 開啟對局歷史的快捷鍵。 */
    private lateinit var historyKeyBinding: KeyBinding

    /** 是否已完成事件註冊。 */
    private var registered = false

    /** 註冊按鍵綁定與 client tick listener；只能在 client entrypoint 呼叫一次。 */
    fun register() {
        if (registered) return
        registered = true
        openKeyBinding = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                MinecraftClientConfigScreenKeys.OPEN_KEY,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_SEMICOLON,
                MinecraftClientConfigScreenKeys.KEY_CATEGORY,
            ),
        )
        historyKeyBinding = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                MinecraftClientConfigScreenKeys.OPEN_HISTORY_KEY,
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.code,
                MinecraftClientConfigScreenKeys.KEY_CATEGORY,
            ),
        )
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /** 從指令開啟設定畫面，取代目前的聊天畫面。 */
    fun openFromCommand() {
        val client = MinecraftClient.getInstance()
        client.execute { open(client, null) }
    }

    /** 消耗快捷鍵事件；其他 Screen 開啟時不搶占使用者輸入。 */
    private fun tick(client: MinecraftClient) {
        while (openKeyBinding.wasPressed()) {
            if (client.currentScreen == null) {
                open(client, null)
            }
        }
        while (historyKeyBinding.wasPressed()) {
            if (client.currentScreen == null) {
                historyScreenController.openFromCommand()
            }
        }
    }

    /** 建立設定畫面並保留指定 parent。 */
    private fun open(client: MinecraftClient, parent: Screen?) {
        client.setScreen(
            MahjongClientConfigScreen(
                parent,
                configStore,
                automaticCoordinator,
                displayResolver,
                preferenceService,
            ),
        )
    }
}
