package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftControlTooltipKeys
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.StringVisitable
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

/**
 * 選擇器展開清單中的一個選項。
 *
 * @property label 選項名稱。
 * @property description 游標停在這個選項上時，清單底部顯示的說明；沒有說明時為 null。
 * @property unavailableReason 不可選的原因；可選時為 null。不可選的選項留在原本的位置，以紅字附上原因。
 */
internal data class DropdownOption(
    val label: Text,
    val description: Text? = null,
    val unavailableReason: Text? = null,
)

/**
 * 一個畫面同時最多展開一份的選擇器清單。
 *
 * 清單通常在選擇器下方展開，下方空間不足時改往上方展開。清單展開期間畫面上其他元件不可操作。使用清單的畫面要做五件事：建立元件時呼叫 [close]；繪製其他內容時
 * 以 [pointer] 取代游標座標，清單展開時略過底層元件的提示框，並在最後呼叫 [render]；`mouseClicked`、`mouseScrolled`、`keyPressed` 先交給這裡，
 * 回傳 true 就不再往下處理；清單展開時（[isOpen]）不處理拖曳與文字輸入。點選
 * 可選的選項、點清單以外的地方或按 Esc 都會收合；選項太多時以滾輪或方向鍵在清單內捲動。選項名稱放不下時截斷，
 * 游標或方向鍵停留在上面時以跑馬燈顯示全名。
 */
internal class DropdownOverlay {
    /** 目前展開的清單；沒有展開時為 null。 */
    private var open: OpenList? = null

    private var lastMouseX: Int? = null
    private var lastMouseY: Int? = null

    /** 是否有清單展開。 */
    val isOpen: Boolean
        get() = open != null

    /**
     * 取得畫面繪製其他元件時應使用的游標座標：清單展開時回傳畫面外的座標，讓底下的元件不顯示游標停留效果
     * 與提示框。
     *
     * @param coordinate 游標實際的水平或垂直座標。
     * @return 清單展開時為畫面外的座標，否則為原座標。
     */
    fun pointer(coordinate: Int): Int = if (isOpen) OFFSCREEN_POINTER else coordinate

    /**
     * 判斷指定的選擇器是否正展開清單。
     *
     * @param key 展開時傳入的選擇器識別值。
     * @return 這個選擇器的清單展開中時為 true。
     */
    fun isOpenFor(key: Any): Boolean = open?.key == key

    /**
     * 展開或收合選擇器的清單：同一個選擇器的清單已展開時收合，否則改為展開它的清單。
     *
     * @param key 選擇器的識別值，畫面重建後仍須相同。
     * @param anchor 選擇器按鈕；清單出現在它的正下方並與它同寬。
     * @param options 依顯示順序排列的選項。
     * @param selectedIndex 目前選項的索引；沒有任何選項符合時為 null。
     * @param onSelect 玩家選定可選的選項時，以其索引呼叫。
     */
    fun toggle(
        key: Any,
        anchor: ClickableWidget,
        options: List<DropdownOption>,
        selectedIndex: Int?,
        onSelect: (Int) -> Unit,
    ) {
        if (isOpenFor(key)) {
            close()
            return
        }
        open = OpenList(
            key = key,
            anchorLeft = anchor.x,
            anchorTop = anchor.y,
            anchorBottom = anchor.y + anchor.height,
            width = anchor.width,
            options = options,
            selectedIndex = selectedIndex,
            highlightedIndex = selectedIndex,
            onSelect = onSelect,
        )
    }

    /** 收合清單。 */
    fun close() {
        open = null
    }

    /**
     * 繪製展開的清單；沒有清單展開時不繪製。
     *
     * @param context 畫面繪製情境。
     * @param textRenderer 文字繪製器。
     * @param windowHeight 視窗高度。
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     */
    fun render(context: DrawContext, textRenderer: TextRenderer, windowHeight: Int, mouseX: Int, mouseY: Int) {
        val mouseMoved = mouseX != lastMouseX || mouseY != lastMouseY
        lastMouseX = mouseX
        lastMouseY = mouseY
        val list = open ?: return
        val footnotes = footnotes(list, textRenderer)
        val layout = layout(list, footnotes, textRenderer, windowHeight)
        if (mouseMoved) {
            list.keyboardHighlight = false
            layout.optionIndexAt(mouseX.toDouble(), mouseY.toDouble())?.let { list.highlightedIndex = it }
        }
        context.matrices.push()
        context.matrices.translate(0f, 0f, OVERLAY_Z)
        context.fill(layout.left, layout.top, layout.right, layout.bottom, BORDER_COLOR)
        context.fill(layout.left + 1, layout.top + 1, layout.right - 1, layout.bottom - 1, BACKGROUND_COLOR)
        val scrollable = layout.maximumScroll > 0
        val textRight = layout.right - 1 - TEXT_PADDING - if (scrollable) VerticalScrollbar.WIDTH + 1 else 0
        repeat(layout.visibleCount) { visibleIndex ->
            val index = layout.effectiveScroll + visibleIndex
            val option = list.options.getOrNull(index) ?: return@repeat
            val rowTop = layout.optionTop(visibleIndex)
            if (index == list.highlightedIndex || index == list.selectedIndex) {
                context.fill(
                    layout.left + 1,
                    rowTop,
                    layout.right - 1,
                    rowTop + DropdownListLayout.OPTION_HEIGHT,
                    if (index == list.highlightedIndex) HIGHLIGHT_COLOR else SELECTED_COLOR,
                )
            }
            val textY = rowTop + (DropdownListLayout.OPTION_HEIGHT - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2
            if (index == list.selectedIndex) {
                PixelIcons.check(
                    context = context,
                    x = layout.left + 1 + TEXT_PADDING,
                    y = rowTop + (DropdownListLayout.OPTION_HEIGHT - PixelIcons.CHECK_HEIGHT) / 2,
                    color = TEXT_COLOR,
                )
            }
            val labelLeft = layout.left + 1 + TEXT_PADDING + CHECK_COLUMN_WIDTH
            val label = option.unavailableReason?.let { reason ->
                Text.translatable(MinecraftControlTooltipKeys.UNAVAILABLE_OPTION, option.label, reason)
            } ?: option.label
            MarqueeText.draw(
                context = context,
                textRenderer = textRenderer,
                text = label,
                left = labelLeft,
                width = (textRight - labelLeft).coerceAtLeast(1),
                y = textY,
                color = if (option.unavailableReason == null) TEXT_COLOR else UNAVAILABLE_TEXT_COLOR,
                state = list.labelMarquees[index],
                subject = Unit.takeIf { index == list.highlightedIndex },
            )
        }
        VerticalScrollbar.render(context, layout.scrollbarLeft, layout.scrollbar)
        val shown = list.highlightedIndex ?: list.selectedIndex
        val shownLines = footnotes.getOrNull(shown ?: -1).orEmpty()
        if (layout.visibleFootnoteLines > 0) {
            context.fill(layout.left + 1, layout.optionsBottom, layout.right - 1, layout.optionsBottom + 1, DIVIDER_COLOR)
        }
        val activated = list.keyboardHighlight || layout.optionIndexAt(mouseX.toDouble(), mouseY.toDouble()) == shown
        drawWrappedDescription(
            context = context,
            renderer = textRenderer,
            lines = shownLines,
            visibleLines = layout.visibleFootnoteLines,
            left = layout.left + 1 + TEXT_PADDING,
            top = layout.footnoteTop,
            width = footnoteWidth(list),
            color = FOOTNOTE_COLOR,
            marquee = list.footnoteMarquee,
            subject = shown.takeIf { activated },
        )
        context.matrices.pop()
    }

    /**
     * 處理滑鼠點擊；清單展開時一律攔下點擊，不讓底下的元件收到。
     *
     * @param textRenderer 文字繪製器。
     * @param windowHeight 視窗高度。
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @param button 滑鼠按鍵代碼。
     * @return 清單展開中時為 true。
     */
    fun mouseClicked(textRenderer: TextRenderer, windowHeight: Int, mouseX: Double, mouseY: Double, button: Int): Boolean {
        val list = open ?: return false
        val layout = layout(list, footnotes(list, textRenderer), textRenderer, windowHeight)
        if (!layout.contains(mouseX, mouseY)) {
            close()
            return true
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true
        layout.optionIndexAt(mouseX, mouseY)?.let { select(list, it) }
        return true
    }

    /**
     * 處理滾輪；清單展開時一律攔下滾輪，選項超出可見範圍時在清單內捲動。
     *
     * @param textRenderer 文字繪製器。
     * @param windowHeight 視窗高度。
     * @param amount 滾輪位移；負值向後看後面的選項。
     * @return 清單展開中時為 true。
     */
    fun mouseScrolled(textRenderer: TextRenderer, windowHeight: Int, amount: Double): Boolean {
        val list = open ?: return false
        val layout = layout(list, footnotes(list, textRenderer), textRenderer, windowHeight)
        if (amount != 0.0) list.scrollIndex = (layout.effectiveScroll + if (amount < 0) 1 else -1).coerceIn(0, layout.maximumScroll)
        return true
    }

    /**
     * 處理鍵盤；清單展開時一律攔下按鍵。Esc 收合，上下方向鍵移動停留的選項，Enter 或空白鍵選定。
     *
     * @param textRenderer 文字繪製器。
     * @param windowHeight 視窗高度。
     * @param keyCode 按鍵代碼。
     * @return 清單展開中時為 true。
     */
    fun keyPressed(textRenderer: TextRenderer, windowHeight: Int, keyCode: Int): Boolean {
        val list = open ?: return false
        when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE -> close()
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                val step = if (keyCode == GLFW.GLFW_KEY_DOWN) 1 else -1
                val next = ((list.highlightedIndex ?: if (step > 0) -1 else list.options.size) + step).coerceIn(0, list.options.lastIndex)
                list.highlightedIndex = next
                list.keyboardHighlight = true
                val layout = layout(list, footnotes(list, textRenderer), textRenderer, windowHeight)
                list.scrollIndex = when {
                    next < layout.effectiveScroll -> next
                    next >= layout.effectiveScroll + layout.visibleCount -> next - layout.visibleCount + 1
                    else -> layout.effectiveScroll
                }
            }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> list.highlightedIndex?.let { select(list, it) }
        }
        return true
    }

    /**
     * 選定選項並收合清單；不可選的選項不處理。
     *
     * @param list 展開中的清單。
     * @param index 選項索引。
     */
    private fun select(list: OpenList, index: Int) {
        val option = list.options.getOrNull(index) ?: return
        if (option.unavailableReason != null) return
        close()
        if (index != list.selectedIndex) list.onSelect(index)
    }

    /**
     * 將每個選項的說明依清單寬度換行。
     *
     * @param list 展開中的清單。
     * @param textRenderer 文字繪製器。
     * @return 依選項順序排列的說明行；沒有說明的選項為空清單。
     */
    private fun footnotes(list: OpenList, textRenderer: TextRenderer): List<List<StringVisitable>> = list.options.map { option ->
        option.description?.let { textRenderer.wrapDescription(it, footnoteWidth(list)) }.orEmpty()
    }

    /** 說明各行共用的水平寬度。 */
    private fun footnoteWidth(list: OpenList): Int = (list.width - DropdownListLayout.BORDER * 2 - TEXT_PADDING * 2).coerceAtLeast(1)

    /**
     * 建立清單版面；底部說明區的高度取所有選項中最高的說明，停留的選項改變時清單大小不變。
     *
     * @param list 展開中的清單。
     * @param footnotes 每個選項換行後的說明。
     * @param textRenderer 文字繪製器。
     * @param windowHeight 視窗高度。
     * @return 清單版面。
     */
    private fun layout(
        list: OpenList,
        footnotes: List<List<StringVisitable>>,
        textRenderer: TextRenderer,
        windowHeight: Int,
    ): DropdownListLayout = DropdownListLayout(
        anchorLeft = list.anchorLeft,
        anchorTop = list.anchorTop,
        anchorBottom = list.anchorBottom,
        width = list.width,
        optionCount = list.options.size,
        footnoteHeight = (footnotes.maxOfOrNull { it.size } ?: 0) * textRenderer.fontHeight,
        windowHeight = windowHeight,
        scrollIndex = list.scrollIndex,
        footnoteLineHeight = textRenderer.fontHeight,
    )

    /**
     * 展開中的清單狀態。
     *
     * @property key 選擇器的識別值。
     * @property anchorLeft 選擇器按鈕的左界。
     * @property anchorTop 選擇器按鈕的上界。
     * @property anchorBottom 選擇器按鈕的下界。
     * @property width 選擇器按鈕的寬度。
     * @property options 依顯示順序排列的選項。
     * @property selectedIndex 目前選項的索引；沒有任何選項符合時為 null。
     * @property highlightedIndex 游標或方向鍵停留的選項索引。
     * @property onSelect 玩家選定選項時的回呼。
     * @property scrollIndex 第一個可見選項的索引。
     * @property keyboardHighlight 是否由方向鍵移到目前的選項。
     * @property footnoteMarquee 說明末行的跑馬燈進度。
     * @property labelMarquees 每個選項名稱的跑馬燈進度；游標或方向鍵停留在放不下的名稱上時捲動顯示全名。
     */
    private class OpenList(
        val key: Any,
        val anchorLeft: Int,
        val anchorTop: Int,
        val anchorBottom: Int,
        val width: Int,
        val options: List<DropdownOption>,
        val selectedIndex: Int?,
        var highlightedIndex: Int?,
        val onSelect: (Int) -> Unit,
        var scrollIndex: Int = 0,
        var keyboardHighlight: Boolean = false,
        val footnoteMarquee: MarqueeState = MarqueeState(),
        val labelMarquees: List<MarqueeState> = options.map { MarqueeState() },
    )

    private companion object {
        /** 清單展開時交給其他元件的游標座標。 */
        const val OFFSCREEN_POINTER = -1

        /** 清單繪製的 Z 位移，讓清單蓋過畫面上的其他元件。 */
        const val OVERLAY_Z = 300f

        /** 清單外框顏色。 */
        const val BORDER_COLOR = 0xFFFFFFFF.toInt()

        /** 清單底色。 */
        const val BACKGROUND_COLOR = 0xFF101010.toInt()

        /** 目前選項與游標停留選項的底色。 */
        const val HIGHLIGHT_COLOR = 0x40FFFFFF

        /** 已套用的選項底色，與目前停留的項目區分。 */
        const val SELECTED_COLOR = 0x1FFFFFFF

        /** 選項與底部說明之間的分隔線顏色。 */
        const val DIVIDER_COLOR = 0x33FFFFFF

        /** 可選選項的文字顏色。 */
        const val TEXT_COLOR = 0xFFFFFF

        /** 不可選選項的文字顏色。 */
        const val UNAVAILABLE_TEXT_COLOR = 0xFF5555

        /** 底部說明的文字顏色。 */
        const val FOOTNOTE_COLOR = 0xBDBDBD

        /** 文字與清單內緣的水平距離。 */
        const val TEXT_PADDING = 5

        /** 選項名稱左側保留給打勾標示的寬度。 */
        const val CHECK_COLUMN_WIDTH = PixelIcons.CHECK_WIDTH + 4
    }
}
