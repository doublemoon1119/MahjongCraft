package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.fabric.client.fitClientText
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/** 各畫面共用的文字與線條繪製：標題、頁籤底線、分隔線、標籤與狀態文字。 */
internal object ScreenRendering {
    /** 一般文字顏色。 */
    const val TEXT_COLOR: Int = 0xFFFFFF

    /** 說明列與唯讀提示的文字顏色。 */
    const val MUTED_TEXT_COLOR: Int = 0xAAAAAA

    /** 錯誤狀態的文字顏色。 */
    const val ERROR_TEXT_COLOR: Int = 0xFF5555

    /** 分類頁籤底線的顏色。 */
    private const val TAB_LINE_COLOR: Int = 0xFFFFFFFF.toInt()

    /** 規則列分隔線的顏色。 */
    private const val DIVIDER_COLOR: Int = 0x40FFFFFF

    /**
     * 繪製置中的畫面標題。
     *
     * @param context 畫面繪製情境。
     * @param textRenderer 文字繪製器。
     * @param title 標題文字。
     * @param windowWidth 視窗寬度。
     */
    fun title(context: GuiGraphics, textRenderer: Font, title: Component, windowWidth: Int) {
        context.drawCenteredString(textRenderer, title, windowWidth / 2, FormScreenLayout.TITLE_Y, FormScreenLayout.TITLE_COLOR)
    }

    /**
     * 繪製分類頁籤列下方貫穿內容寬度的底線。
     *
     * @param context 畫面繪製情境。
     * @param strip 頁籤列版面。
     */
    fun tabLine(context: GuiGraphics, strip: TabStripLayout) {
        context.fill(strip.left, strip.lineY, strip.right, strip.lineY + 1, TAB_LINE_COLOR)
    }

    /**
     * 繪製規則列下方的分隔線。
     *
     * @param context 畫面繪製情境。
     * @param layout 畫面版面。
     * @param y 分隔線的 Y 座標。
     */
    fun divider(context: GuiGraphics, layout: FormScreenLayout, y: Int) {
        context.fill(layout.contentLeft, y, layout.contentRight, y + 1, DIVIDER_COLOR)
    }

    /**
     * 繪製靠左的標籤；放不下時截斷並加上省略號。
     *
     * @param context 畫面繪製情境。
     * @param textRenderer 文字繪製器。
     * @param label 標籤全文。
     * @param x 標籤左界。
     * @param y 標籤文字的 Y 座標。
     * @param maximumWidth 標籤可用的寬度。
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @param color 文字顏色。
     * @return 標籤被截斷且游標停在上面時為標籤全文，供提示框顯示；否則為 null。
     */
    fun label(
        context: GuiGraphics,
        textRenderer: Font,
        label: Component,
        x: Int,
        y: Int,
        maximumWidth: Int,
        mouseX: Int,
        mouseY: Int,
        color: Int = TEXT_COLOR,
    ): Component? {
        val fitted = fitClientText(textRenderer, label, maximumWidth)
        context.drawString(textRenderer, fitted, x, y, color)
        val truncated = fitted.string != label.string
        val hovered = mouseX in x until x + maximumWidth && mouseY in y until y + textRenderer.lineHeight
        return label.takeIf { truncated && hovered }
    }

    /**
     * 在底部按鈕列上方繪製置中的狀態文字；放不下時截斷，游標停在上面時以跑馬燈顯示全文。
     *
     * @param context 畫面繪製情境。
     * @param textRenderer 文字繪製器。
     * @param layout 畫面版面。
     * @param message 狀態文字。
     * @param color 文字顏色。
     * @param marquee 狀態文字的跑馬燈進度。
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     */
    fun status(
        context: GuiGraphics,
        textRenderer: Font,
        layout: FormScreenLayout,
        message: Component,
        color: Int,
        marquee: MarqueeState,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = mouseX in layout.contentLeft until layout.contentRight &&
            mouseY in layout.statusY until layout.statusY + textRenderer.lineHeight
        MarqueeText.draw(
            context = context,
            textRenderer = textRenderer,
            text = message,
            left = layout.contentLeft,
            width = layout.contentWidth,
            y = layout.statusY,
            color = color,
            state = marquee,
            subject = message.string.takeIf { hovered },
            centered = true,
        )
    }
}
