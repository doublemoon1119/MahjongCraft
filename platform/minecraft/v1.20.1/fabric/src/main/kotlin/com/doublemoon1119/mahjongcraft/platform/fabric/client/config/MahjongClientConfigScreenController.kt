package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.lwjgl.glfw.GLFW

/** 集中註冊並處理 Client Config Screen 的快捷鍵與程式化開啟入口。 */
@Single
class MahjongClientConfigScreenController(
    private val configStore: MahjongClientConfigStore,
    @Provided private val json: Json,
) {
    /** 預設以分號開啟 Client Config Screen 的按鍵綁定。 */
    private lateinit var openKeyBinding: KeyBinding

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
    }

    /** 建立設定畫面並保留指定 parent。 */
    private fun open(client: MinecraftClient, parent: Screen?) {
        client.setScreen(MahjongClientConfigScreen(parent, configStore, json))
    }
}
