package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.fabric.client.fitClientText
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.time.Duration

/**
 * 一段可能放不下的文字的跑馬燈進度：記住目前正在完整顯示的對象，對象改變時從頭播放。
 */
internal class MarqueeState {
    /** 上一幀正在完整顯示的對象；沒有時為 null。 */
    private var subject: Any? = null

    /** 開始完整顯示目前對象的單調時鐘時間。 */
    private var startedAt: Duration = Duration.ZERO

    /**
     * 取得這一幀的位移，並記住目前的對象。
     *
     * @param subject 這一幀要完整顯示的對象，例如游標停留的選項；不需要完整顯示時為 null。
     * @param overflow 文字超出可用寬度的像素數。
     * @return 介於 0 與 [overflow] 之間的位移；沒有對象時為 0。對象與上一幀不同時從頭開始。
     */
    fun offset(subject: Any?, overflow: Int): Int {
        if (subject != this.subject) {
            this.subject = subject
            startedAt = measuringTimeNow()
        }
        if (subject == null) return 0
        return MarqueeTiming.offset((measuringTimeNow() - startedAt).coerceAtLeast(Duration.ZERO), overflow)
    }
}

/** 繪製可能放不下的單行文字：放不下時截斷並加上省略號，要完整顯示時改為跑馬燈。 */
internal object MarqueeText {
    /**
     * 在指定範圍內繪製單行文字。
     *
     * @param context 畫面繪製情境。
     * @param textRenderer 文字繪製器。
     * @param text 文字全文。
     * @param left 可用範圍的左界。
     * @param width 可用範圍的寬度。
     * @param y 文字的 Y 座標。
     * @param color 文字顏色。
     * @param state 這段文字的跑馬燈進度。
     * @param subject 要完整顯示時代表這段文字的對象，例如游標停留的選項；不需要完整顯示時為 null。
     * @param centered 文字放得下或被截斷時是否在範圍內置中；否則靠左。
     */
    fun draw(
        context: GuiGraphics,
        textRenderer: Font,
        text: Component,
        left: Int,
        width: Int,
        y: Int,
        color: Int,
        state: MarqueeState,
        subject: Any?,
        centered: Boolean = false,
    ) {
        val overflow = textRenderer.width(text) - width
        val scrollX = state.offset(subject.takeIf { overflow > 0 }, overflow)
        if (overflow <= 0 || subject == null) {
            val fitted = fitClientText(textRenderer, text, width)
            val x = if (centered) left + (width - textRenderer.width(fitted)) / 2 else left
            context.drawString(textRenderer, fitted, x, y, color)
            return
        }
        context.enableScissor(left, y - 1, left + width, y + textRenderer.lineHeight + 1)
        context.drawString(textRenderer, text, left - scrollX, y, color)
        context.disableScissor()
    }
}
