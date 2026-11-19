package com.doublemoon1119.mahjongcraft.platform.fabric.client.handbook

import com.doublemoon1119.mahjongcraft.platform.fabric.client.fitClientText
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 麻將手冊快捷畫面的入口按鈕：一般按鈕底，左側物品圖示，右側名稱與一行灰色說明；放不下的文字截斷，
 * 說明被截斷時以提示框顯示全文。
 *
 * @param x 按鈕左界。
 * @param y 按鈕上界。
 * @param width 按鈕寬度。
 * @param height 按鈕高度。
 * @property entry 這個按鈕代表的入口。
 * @param onPress 按下時執行的動作。
 */
internal class HandbookEntryButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val entry: HandbookEntry,
    onPress: PressAction,
) : ButtonWidget(
    x,
    y,
    width,
    height,
    Text.translatable(entry.titleKey),
    onPress,
    DEFAULT_NARRATION_SUPPLIER,
) {
    /** 入口名稱。 */
    private val title: Text = Text.translatable(entry.titleKey)

    /** 入口說明。 */
    private val description: Text = Text.translatable(entry.descriptionKey)

    /** 上次設定提示框時說明是否被截斷，避免每幀重建提示框。 */
    private var descriptionTruncated: Boolean? = null

    override fun renderButton(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val originalMessage = message
        try {
            message = Text.empty()
            super.renderButton(context, mouseX, mouseY, delta)
        } finally {
            message = originalMessage
        }
        val textRenderer = MinecraftClient.getInstance().textRenderer
        context.drawItem(entry.icon, x + ICON_INSET, y + (height - ICON_SIZE) / 2)
        val textLeft = x + ICON_INSET + ICON_SIZE + ICON_TEXT_GAP
        val textWidth = (x + width - TEXT_RIGHT_INSET - textLeft).coerceAtLeast(1)
        val color = if (active) TITLE_COLOR else DISABLED_COLOR
        context.drawTextWithShadow(textRenderer, fitClientText(textRenderer, title, textWidth), textLeft, y + TITLE_TOP, color)
        context.drawTextWithShadow(textRenderer, fitClientText(textRenderer, description, textWidth), textLeft, y + DESCRIPTION_TOP, DESCRIPTION_COLOR)
        val truncated = textRenderer.getWidth(description) > textWidth
        if (truncated != descriptionTruncated) {
            descriptionTruncated = truncated
            tooltip = if (truncated) Tooltip.of(description.copy().formatted(Formatting.GRAY)) else null
        }
    }

    /** 尺寸與顏色。 */
    private companion object {
        /** 物品圖示的邊長。 */
        const val ICON_SIZE = 16

        /** 圖示與按鈕左緣的距離。 */
        const val ICON_INSET = 8

        /** 圖示與文字之間的距離。 */
        const val ICON_TEXT_GAP = 8

        /** 文字與按鈕右緣的距離。 */
        const val TEXT_RIGHT_INSET = 6

        /** 名稱距按鈕上緣的距離。 */
        const val TITLE_TOP = 6

        /** 說明距按鈕上緣的距離。 */
        const val DESCRIPTION_TOP = 18

        /** 名稱顏色。 */
        const val TITLE_COLOR = 0xFFFFFF

        /** 停用時的名稱顏色。 */
        const val DISABLED_COLOR = 0xA0A0A0

        /** 說明顏色。 */
        const val DESCRIPTION_COLOR = 0xC8C8C8
    }
}
