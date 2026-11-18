package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.PressableWidget
import net.minecraft.text.Text

/**
 * 分類頁籤列中的一個頁籤。
 *
 * 選中的頁籤使用一般按鈕的底色、白字與白色外框，沒有底邊，與頁籤列的底線相連；未選中的頁籤較矮，
 * 使用深灰底與淺灰字。頁籤列可左右捲動時，超出頁籤列的部分不繪製也不能點擊。
 *
 * 名稱放不下時截斷並加上省略號；游標停在頁籤上或頁籤取得焦點時，改以跑馬燈捲動顯示完整名稱。
 *
 * @param x 頁籤左界。
 * @param y 頁籤上界。
 * @param width 頁籤寬度。
 * @param height 頁籤高度。
 * @param message 頁籤文字。
 * @property selected 是否為目前選中的頁籤。
 * @property clipLeft 頁籤列可見範圍的左界。
 * @property clipRight 頁籤列可見範圍的右界。
 * @property onFocus 取得焦點時執行的動作。
 * @property onSelect 點擊頁籤時執行的動作。
 */
internal class TabWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Text,
    private val selected: Boolean,
    private val clipLeft: Int,
    private val clipRight: Int,
    private val onFocus: () -> Unit = {},
    private val onSelect: () -> Unit,
) : PressableWidget(x, y, width, height, message) {
    /** 頁籤名稱的跑馬燈進度。 */
    private val labelMarquee = MarqueeState()

    override fun setFocused(focused: Boolean) {
        val gainedFocus = focused && !isFocused
        super.setFocused(focused)
        if (gainedFocus) onFocus()
    }

    override fun onPress() {
        if (!selected) onSelect()
    }

    override fun renderButton(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val visibleLeft = x.coerceAtLeast(clipLeft)
        val visibleRight = (x + width).coerceAtMost(clipRight)
        if (visibleLeft >= visibleRight) return
        val hoveredNow = mouseX in visibleLeft until visibleRight && mouseY in y until y + height
        context.enableScissor(visibleLeft, y, visibleRight, y + height)
        val border = if (selected || isFocused) SELECTED_BORDER_COLOR else UNSELECTED_BORDER_COLOR
        val fill = when {
            selected -> SELECTED_FILL_COLOR
            hoveredNow -> HOVERED_FILL_COLOR
            else -> UNSELECTED_FILL_COLOR
        }
        context.fill(x, y, x + width, y + height, border)
        context.fill(x + 1, y + 1, x + width - 1, y + height, fill)
        if (selected) {
            context.fill(x + 1, y + 1, x + width - 1, y + 2, SELECTED_HIGHLIGHT_COLOR)
            context.fill(x + 1, y + 1, x + 2, y + height, SELECTED_HIGHLIGHT_COLOR)
        }
        MarqueeText.draw(
            context = context,
            textRenderer = MinecraftClient.getInstance().textRenderer,
            text = message,
            left = x + TEXT_PADDING,
            width = (width - TEXT_PADDING * 2).coerceAtLeast(1),
            y = y + (height - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2,
            color = if (selected || hoveredNow) SELECTED_TEXT_COLOR else UNSELECTED_TEXT_COLOR,
            state = labelMarquee,
            subject = Unit.takeIf { hoveredNow || isFocused },
            centered = true,
        )
        context.disableScissor()
    }

    override fun clicked(mouseX: Double, mouseY: Double): Boolean = mouseX >= clipLeft && mouseX < clipRight && super.clicked(mouseX, mouseY)

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean = mouseX >= clipLeft && mouseX < clipRight && super.isMouseOver(mouseX, mouseY)

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) = appendDefaultNarrations(builder)

    private companion object {
        /** 文字左右預留的像素數。 */
        const val TEXT_PADDING = 3

        /** 選中或取得焦點的頁籤外框顏色。 */
        const val SELECTED_BORDER_COLOR = 0xFFFFFFFF.toInt()

        /** 未選中的頁籤外框顏色。 */
        const val UNSELECTED_BORDER_COLOR = 0xFF000000.toInt()

        /** 選中的頁籤底色。 */
        const val SELECTED_FILL_COLOR = 0xFF6F6F6F.toInt()

        /** 選中的頁籤左側與上側的亮邊顏色。 */
        const val SELECTED_HIGHLIGHT_COLOR = 0xFFA9A9A9.toInt()

        /** 游標停留的未選中頁籤底色。 */
        const val HOVERED_FILL_COLOR = 0xFF4E4E4E.toInt()

        /** 未選中的頁籤底色。 */
        const val UNSELECTED_FILL_COLOR = 0xFF3A3A3A.toInt()

        /** 選中或游標停留的頁籤文字顏色。 */
        const val SELECTED_TEXT_COLOR = 0xFFFFFF

        /** 未選中的頁籤文字顏色。 */
        const val UNSELECTED_TEXT_COLOR = 0xC8C8C8
    }
}

/** 依頁籤列的捲動位置更新現有按鈕，保留鍵盤焦點。 */
internal fun List<TabWidget>.applyTabScroll(strip: TabStripLayout) {
    forEachIndexed { index, widget -> widget.x = strip.tabX(index) }
}
