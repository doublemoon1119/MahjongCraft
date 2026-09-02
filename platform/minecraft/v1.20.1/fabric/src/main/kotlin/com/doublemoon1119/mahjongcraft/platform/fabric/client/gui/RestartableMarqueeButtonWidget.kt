package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Util
import kotlin.math.roundToInt

/** 文字過寬時捲動，並在游標每次重新進入後從頭播放的按鈕。 */
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

    override fun renderButton(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val originalMessage = message
        try {
            message = Text.empty()
            super.renderButton(context, mouseX, mouseY, delta)
        } finally {
            message = originalMessage
        }

        val hoveredNow = isHovered
        if (hoveredNow && !hoveredLastFrame) hoverStartedAt = Util.getMeasuringTimeMs()
        hoveredLastFrame = hoveredNow
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
        val scrollX = marqueeOffset(elapsed, overflow)
        context.enableScissor(x + HORIZONTAL_PADDING, y, x + width - HORIZONTAL_PADDING, y + height)
        context.drawTextWithShadow(textRenderer, text, x + HORIZONTAL_PADDING - scrollX, textY, color)
        context.disableScissor()
    }

    private fun marqueeOffset(elapsed: Long, overflow: Int): Int {
        if (elapsed <= START_PAUSE_MILLIS) return 0
        val travelMillis = (overflow * MILLIS_PER_PIXEL).coerceAtLeast(1L)
        val cycleMillis = travelMillis * 2 + END_PAUSE_MILLIS * 2
        val cyclePosition = (elapsed - START_PAUSE_MILLIS) % cycleMillis
        return when {
            cyclePosition < travelMillis -> (cyclePosition.toDouble() / travelMillis * overflow).roundToInt()
            cyclePosition < travelMillis + END_PAUSE_MILLIS -> overflow
            cyclePosition < travelMillis * 2 + END_PAUSE_MILLIS ->
                (overflow - (cyclePosition - travelMillis - END_PAUSE_MILLIS).toDouble() / travelMillis * overflow).roundToInt()
            else -> 0
        }
    }

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
        private const val START_PAUSE_MILLIS = 350L
        private const val END_PAUSE_MILLIS = 450L
        private const val MILLIS_PER_PIXEL = 35L

        fun builder(message: Text, onPress: PressAction): Builder = Builder(message, onPress)
    }
}
