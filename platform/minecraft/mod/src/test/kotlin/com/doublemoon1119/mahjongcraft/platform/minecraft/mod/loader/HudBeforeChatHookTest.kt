package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.client.gui.GuiGraphics
import sun.misc.Unsafe
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
        // 通知只轉交繪製環境、不使用它；不啟動遊戲無法正常建立，因此略過建構子配置一個空的物件。
        val context = unsafe().allocateInstance(GuiGraphics::class.java) as GuiGraphics

        HudBeforeChatHook.fire(context)

        assertEquals(listOf("first", "second"), received.takeLast(2).map { it.first })
        received.takeLast(2).forEach { assertSame(context, it.second) }
    }

    /**
     * 取得 JDK 內部用來配置物件的工具。
     *
     * @return 可以略過建構子配置物件的工具。
     */
    private fun unsafe(): Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
}
