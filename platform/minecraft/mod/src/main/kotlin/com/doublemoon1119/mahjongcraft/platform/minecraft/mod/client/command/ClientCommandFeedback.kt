package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.command

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/** 用戶端指令的回饋訊息：顯示在聊天欄並交給旁白朗讀，不經過指令來源。 */
object ClientCommandFeedback {
    /**
     * 顯示一般回饋。
     *
     * @param message 回饋訊息。
     */
    fun send(message: Component) {
        val client = Minecraft.getInstance()
        client.gui.chat.addMessage(message)
        client.narrator.sayNow(message)
    }

    /**
     * 以紅字顯示錯誤回饋。
     *
     * @param message 錯誤訊息。
     */
    fun error(message: Component) {
        send(Component.empty().append(message).withStyle(ChatFormatting.RED))
    }
}
