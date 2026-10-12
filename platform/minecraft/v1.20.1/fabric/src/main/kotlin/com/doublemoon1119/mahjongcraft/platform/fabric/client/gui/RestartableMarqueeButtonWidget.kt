package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.tooltip.TooltipPositioner
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Util

/**
 * 文字過寬時捲動，並在游標每次重新進入後從頭播放的按鈕。
 *
 * 提示框超出畫面上下緣時推回畫面內；比畫面還高時上下捲動，游標重新進入或重新取得焦點後從頂端開始。
 *
 * @param x 按鈕左界。
 * @param y 按鈕上界。
 * @param width 按鈕寬度。
 * @param height 按鈕高度。
 * @param message 按鈕文字。
 * @param onPress 按下按鈕時執行的動作。
 */
internal class RestartableMarqueeButtonWidget private constructor(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Text,
    onPress: PressAction,
) : ButtonWidget(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER) {
    /** 上一幀游標是否位於按鈕上，用於偵測重新進入。 */
    private var hoveredLastFrame = false

    /** 本次游標進入按鈕的單調時鐘毫秒數。 */
    private var hoverStartedAt = 0L

    /** 上一幀是否處於選取狀態，用於偵測重新取得焦點。 */
    private var selectedLastFrame = false

    /** 本次提示框播放起點的單調時鐘毫秒數。 */
    private var tooltipStartedAt = 0L

    override fun renderButton(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val originalMessage = message
        try {
            message = Text.empty()
            super.renderButton(context, mouseX, mouseY, delta)
        } finally {
            message = originalMessage
        }

        val hoveredNow = isHovered
        val selectedNow = isSelected
        if (hoveredNow && !hoveredLastFrame) hoverStartedAt = Util.getMeasuringTimeMs()
        if ((hoveredNow && !hoveredLastFrame) || (selectedNow && !selectedLastFrame)) tooltipStartedAt = Util.getMeasuringTimeMs()
        hoveredLastFrame = hoveredNow
        selectedLastFrame = selectedNow
        renderMessage(context, originalMessage, hoveredNow)
    }

    /**
     * 繪製按鈕文字；文字過寬時裁切，且只在游標停留期間播放跑馬燈。
     *
     * @param context 本幀繪製環境。
     * @param text 欲繪製的文字。
     * @param hovered 游標是否位於按鈕上。
     */
    private fun renderMessage(context: DrawContext, text: Text, hovered: Boolean) {
        val textRenderer = MinecraftClient.getInstance().textRenderer
        val availableWidth = (width - HORIZONTAL_PADDING * 2).coerceAtLeast(1)
        val textWidth = textRenderer.getWidth(text)
        val textY = y + (height - VANILLA_VISIBLE_TEXT_HEIGHT) / 2
        val color = if (active) 0xFFFFFF else 0xA0A0A0
        if (textWidth <= availableWidth) {
            context.drawTextWithShadow(textRenderer, text, x + (width - textWidth) / 2, textY, color)
            return
        }

        val overflow = textWidth - availableWidth
        val elapsed = if (hovered) (Util.getMeasuringTimeMs() - hoverStartedAt).coerceAtLeast(0L) else 0L
        val scrollX = MarqueeTiming.offset(elapsed, overflow)
        context.enableScissor(x + HORIZONTAL_PADDING, y, x + width - HORIZONTAL_PADDING, y + height)
        context.drawTextWithShadow(textRenderer, text, x + HORIZONTAL_PADDING - scrollX, textY, color)
        context.disableScissor()
    }

    override fun getTooltipPositioner(): TooltipPositioner = ScreenFittingTooltipPositioner(
        base = super.getTooltipPositioner(),
        shownSinceMillis = tooltipStartedAt,
    )

    /**
     * 設定按鈕的位置與尺寸；預設位於原點，寬 150、高 20。
     *
     * @property message 按鈕文字。
     * @property onPress 按下按鈕時執行的動作。
     */
    internal class Builder(
        private val message: Text,
        private val onPress: PressAction,
    ) {
        /** 按鈕左界。 */
        private var x = 0

        /** 按鈕上界。 */
        private var y = 0

        /** 按鈕寬度。 */
        private var width = 150

        /** 按鈕高度。 */
        private var height = 20

        /**
         * 設定按鈕矩形範圍。
         *
         * @param x 左界。
         * @param y 上界。
         * @param width 寬度。
         * @param height 高度。
         * @return 此建構器，供串接設定。
         */
        fun dimensions(x: Int, y: Int, width: Int, height: Int): Builder = apply {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
        }

        /** 依目前設定建立新的按鈕。 */
        fun build(): RestartableMarqueeButtonWidget = RestartableMarqueeButtonWidget(x, y, width, height, message, onPress)
    }

    /** 按鈕的繪製尺寸與建構入口。 */
    companion object {
        /** 文字左右預留的像素數。 */
        private const val HORIZONTAL_PADDING = 4

        /** 用於垂直置中文字的可見字高。 */
        private const val VANILLA_VISIBLE_TEXT_HEIGHT = 8

        /**
         * 建立按鈕建構器。
         *
         * @param message 按鈕文字。
         * @param onPress 按下按鈕時執行的動作。
         * @return 使用預設位置與尺寸的建構器。
         */
        fun builder(message: Text, onPress: PressAction): Builder = Builder(message, onPress)
    }
}
