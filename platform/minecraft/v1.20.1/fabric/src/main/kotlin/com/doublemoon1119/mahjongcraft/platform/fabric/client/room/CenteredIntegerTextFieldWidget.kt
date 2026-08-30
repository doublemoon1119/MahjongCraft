package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text

/** 保留原版輸入、游標與選取行為，並將短整數文字置中的文字輸入框。 */
internal class CenteredIntegerTextFieldWidget(
    private val fieldTextRenderer: TextRenderer,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    narration: Text,
) : TextFieldWidget(fieldTextRenderer, x, y, width, height, narration) {
    init {
        setDrawsBackground(false)
    }

    override fun renderButton(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val originalX = x
        val originalY = y
        val originalWidth = width
        val originalHeight = height
        val borderColor = if (isFocused) 0xFFFFFFFF.toInt() else 0xFFA0A0A0.toInt()
        context.fill(originalX, y, originalX + originalWidth, y + height, borderColor)
        context.fill(originalX + 1, y + 1, originalX + originalWidth - 1, y + height - 1, 0xFF000000.toInt())

        val contentWidth = (originalWidth - 8).coerceAtLeast(1)
        val centeredPadding = ((contentWidth - fieldTextRenderer.getWidth(text)) / 2).coerceAtLeast(0)
        try {
            x = originalX + 4 + centeredPadding
            y = originalY + (originalHeight - VANILLA_VISIBLE_TEXT_HEIGHT) / 2
            width = (contentWidth - centeredPadding * 2).coerceAtLeast(1)
            height = VANILLA_VISIBLE_TEXT_HEIGHT
            super.renderButton(context, mouseX, mouseY, delta)
        } finally {
            x = originalX
            y = originalY
            width = originalWidth
            height = originalHeight
        }
    }

    private companion object {
        /** 原版 TextFieldWidget 有背景時用來計算文字基準線的可視字高。 */
        const val VANILLA_VISIBLE_TEXT_HEIGHT = 8
    }
}
