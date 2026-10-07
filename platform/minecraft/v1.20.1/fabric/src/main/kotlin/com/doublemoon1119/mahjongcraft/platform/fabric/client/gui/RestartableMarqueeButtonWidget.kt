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
 */
internal class RestartableMarqueeButtonWidget private constructor(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Text,
    onPress: PressAction,
) : ButtonWidget(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER) {
    private var hoveredLastFrame = false
    private var hoverStartedAt = 0L
    private var selectedLastFrame = false
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

    internal class Builder(
        private val message: Text,
        private val onPress: PressAction,
    ) {
        private var x = 0
        private var y = 0
        private var width = 150
        private var height = 20

        fun dimensions(x: Int, y: Int, width: Int, height: Int): Builder = apply {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
        }

        fun build(): RestartableMarqueeButtonWidget = RestartableMarqueeButtonWidget(x, y, width, height, message, onPress)
    }

    companion object {
        private const val HORIZONTAL_PADDING = 4
        private const val VANILLA_VISIBLE_TEXT_HEIGHT = 8

        fun builder(message: Text, onPress: PressAction): Builder = Builder(message, onPress)
    }
}
