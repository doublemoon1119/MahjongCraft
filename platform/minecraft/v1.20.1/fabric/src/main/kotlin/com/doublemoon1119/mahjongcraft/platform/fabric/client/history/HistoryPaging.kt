package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 牌譜各頁底部翻頁的共用呈現：上一頁、下一頁按鈕的提示，以及中間一格的範圍文字。
 *
 * 範圍文字第一行寫目前看到的範圍，例如「第 21–40 筆」；知道總數時第二行以灰字寫「共 57 筆」。
 */
internal object HistoryPaging {
    /** 範圍文字第二行的顏色。 */
    private const val SECONDARY_COLOR = 0xAAAAAA

    /** 範圍文字第一行的顏色。 */
    private const val PRIMARY_COLOR = 0xFFFFFF

    /** 兩行範圍文字的行距。 */
    private const val LINE_STEP = 10

    /**
     * 判斷這次點擊是否按住 Shift，用來跳到第一頁或最後一頁。
     *
     * @return 按住 Shift 時為 true。
     */
    fun jumpsToEnd(): Boolean = Screen.hasShiftDown()

    /**
     * 建立翻頁按鈕的提示：點擊與按住 Shift 點擊的操作，查詢中或冷卻中時再加一行黃字。
     *
     * @param clickKey 點擊的操作提示翻譯鍵。
     * @param shiftKey 按住 Shift 點擊的操作提示翻譯鍵。
     * @param status 查詢中或冷卻中的說明；沒有時為 null。
     * @return 提示文字。
     */
    fun buttonTooltip(clickKey: String, shiftKey: String, status: Component?): Component {
        val text = Component.empty()
            .append(Component.translatable(clickKey).withStyle(ChatFormatting.GRAY))
            .append("\n")
            .append(Component.translatable(shiftKey).withStyle(ChatFormatting.GRAY))
        status?.let { text.append("\n\n").append(it.copy().withStyle(ChatFormatting.YELLOW)) }
        return text
    }

    /**
     * 建立範圍文字下方的總數。
     *
     * @param total 總數；未知時為 null。
     * @return 「共 N 筆」；未知時為 null。
     */
    fun totalText(total: Int?): Component? = total?.let { Component.translatable(MinecraftHistoryScreenKeys.RANGE_TOTAL, it) }

    /**
     * 在底部中間一格置中繪製範圍文字；文字太寬時等比縮小，只有一行時垂直置中。
     *
     * @param context 繪製上下文。
     * @param textRenderer 文字繪製器。
     * @param bounds 中間一格的範圍。
     * @param primary 第一行，例如「第 21–40 筆」。
     * @param secondary 第二行，例如「共 57 筆」；沒有時為 null。
     */
    fun renderRange(context: GuiGraphics, textRenderer: Font, bounds: HistoryScreenLayout.Bounds, primary: Component, secondary: Component?) {
        val lines = listOfNotNull(primary to PRIMARY_COLOR, secondary?.let { it to SECONDARY_COLOR })
        val top = bounds.y + (bounds.height - (lines.size - 1) * LINE_STEP - textRenderer.lineHeight) / 2 + 1
        context.enableScissor(bounds.x, bounds.y, bounds.right, bounds.bottom)
        lines.forEachIndexed { index, (line, color) ->
            val scale = (bounds.width.toFloat() / textRenderer.width(line).coerceAtLeast(1)).coerceAtMost(1f)
            context.pose().pushPose()
            context.pose().translate((bounds.x + bounds.width / 2).toDouble(), (top + index * LINE_STEP).toDouble(), 0.0)
            context.pose().scale(scale, scale, 1f)
            context.drawCenteredString(textRenderer, line, 0, 0, color)
            context.pose().popPose()
        }
        context.disableScissor()
    }
}
