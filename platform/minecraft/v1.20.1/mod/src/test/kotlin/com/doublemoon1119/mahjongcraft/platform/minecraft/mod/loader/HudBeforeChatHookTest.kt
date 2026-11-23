package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.MultiBufferSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 驗證聊天欄之前的時間點把同一個繪製環境依登記順序交給每個監聽器。 */
class HudBeforeChatHookTest {
    /** 每次通知都依登記順序呼叫全部監聽器，並交出同一個繪製環境。 */
    @Test
    fun `fire passes the drawing context to every listener in order`() {
        val received = mutableListOf<Pair<String, GuiGraphics>>()
        HudBeforeChatHook.addListener { received += "first" to it }
        HudBeforeChatHook.addListener { received += "second" to it }
        // 原版的建構子只保存參數，測試不需要啟動遊戲即可建立繪製環境；參數在原版標為不可為 null，因此以反射傳入 null。
        val context = GuiGraphics::class.java
            .getConstructor(Minecraft::class.java, MultiBufferSource.BufferSource::class.java)
            .newInstance(null, null)

        HudBeforeChatHook.fire(context)

        assertEquals(listOf("first", "second"), received.takeLast(2).map { it.first })
        received.takeLast(2).forEach { assertSame(context, it.second) }
    }
}
