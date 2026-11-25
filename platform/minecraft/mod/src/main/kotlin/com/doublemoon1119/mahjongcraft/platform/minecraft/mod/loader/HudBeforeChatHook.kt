package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.client.gui.GuiGraphics
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 每一幀原版遊戲畫面要畫聊天欄之前的時間點。
 *
 * 原版沒有這個時間點的事件，由共用的 `GuiMixin` 在原版 `Gui.render` 中呼叫 [fire]；以原版 `Gui.render` 繪製遊戲畫面的載入器，
 * 其 [ClientEvents.onHudRenderBeforeChat] 把監聽器登記到這裡。
 */
object HudBeforeChatHook {
    /** 聊天欄之前的監聽器。 */
    private val listeners = CopyOnWriteArrayList<(GuiGraphics) -> Unit>()

    /**
     * 登記聊天欄之前的繪製。
     *
     * @param listener 收到本幀繪製環境的處理。
     */
    fun addListener(listener: (GuiGraphics) -> Unit) {
        listeners += listener
    }

    /**
     * 通知原版即將畫聊天欄；只由 `GuiMixin` 呼叫。
     *
     * @param context 本幀繪製環境。
     */
    @JvmStatic
    fun fire(context: GuiGraphics) {
        listeners.forEach { it(context) }
    }
}
