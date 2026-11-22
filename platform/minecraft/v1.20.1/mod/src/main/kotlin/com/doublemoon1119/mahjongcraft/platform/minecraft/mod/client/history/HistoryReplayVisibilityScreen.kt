package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOption
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOverlay
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW

/**
 * 開啟可見性畫面時回放場景的座位與視角。
 *
 * @property seats 各家的初始座位與名稱，依座位順序。
 * @property viewedSeat 目前視角所在那一家的初始座位；自由視角為 null。
 * @property viewLabel 目前視角的名稱。
 */
internal data class HistoryReplayVisibilityContext(
    val seats: List<Seat>,
    val viewedSeat: Int?,
    val viewLabel: Component,
) {
    /**
     * 一家的初始座位與名稱。
     *
     * @property initialSeat 初始座位。
     * @property label 座位風與玩家名稱。
     */
    data class Seat(val initialSeat: Int, val label: Component)
}

/**
 * 牌譜回放的可見性子頁：分「人物」「遊戲面板」「桌上物件」「特效」四段，每列左邊是名稱、右邊是選擇器，改了立即生效；
 * 底部是「恢復預設值」與「完成」。視角所在那一家的人物與玩家資訊固定隱藏並停用。
 *
 * @property session 此畫面所屬的共用瀏覽 session。
 * @property replay 開啟此畫面的回放畫面，完成後回到它。
 * @property context 開啟時回放場景的座位與視角。
 */
internal class HistoryReplayVisibilityScreen(
    private val session: HistoryBrowseSession,
    private val replay: HistoryReplayScreen,
    private val context: HistoryReplayVisibilityContext,
) : Screen(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY)) {
    /** 顯示設定。 */
    private val visibility: HistoryReplayVisibility get() = session.replayVisibility

    /** 由上到下的列。 */
    private val rows: List<VisibilityRow> = buildList {
        add(VisibilityRow.Heading(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_FIGURES))
        context.seats.forEach { add(VisibilityRow.Figure(it)) }
        add(VisibilityRow.Heading(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_PANELS))
        add(VisibilityRow.RoundInfo)
        context.seats.forEach { add(VisibilityRow.PlayerInfo(it)) }
        add(VisibilityRow.Heading(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_TABLE_PROPS))
        add(VisibilityRow.TableProps)
        add(VisibilityRow.Heading(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_EFFECTS))
        add(VisibilityRow.WinEffect)
    }

    /** 各列的選擇器；標題列沒有。 */
    private val selectors = mutableMapOf<VisibilityRow, RestartableMarqueeButtonWidget>()

    /** 選擇器展開的清單。 */
    private val dropdown = DropdownOverlay()

    /** 第一個可見列的索引。 */
    private var scrollIndex = 0

    /** 是否正在拖曳捲軸。 */
    private var scrollDragging = false

    /** 拖曳時游標在捲軸滑塊內的抓取偏移。 */
    private var scrollGrabOffset = 0.0

    /** 恢復預設值按鈕。 */
    private var resetButton: RestartableMarqueeButtonWidget? = null

    /** 目前畫面的版面；標題下方留一行目前視角。 */
    private val layout: FormScreenLayout
        get() = FormScreenLayout(windowWidth = width, windowHeight = height, hasPageSwitch = false, hasRuleRow = false, hasTabs = false, headerHeight = HEADER_HEIGHT)

    override fun init() {
        dropdown.close()
        selectors.clear()
        scrollDragging = false
        rows.filterNot { it is VisibilityRow.Heading }.forEach { row ->
            lateinit var selector: RestartableMarqueeButtonWidget
            selector = RestartableMarqueeButtonWidget.builder(Component.empty()) { toggle(row, selector) }.build()
            selector.selectorExpanded = { dropdown.isOpenFor(row) }
            selectors[row] = addRenderableWidget(selector)
        }
        scrollIndex = scrollIndex.coerceIn(0, maximumScroll())
        val footer = layout.footer(buttonCount = 2)
        resetButton = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_RESET)) { visibility.reset() }
                .dimensions(footer.buttonX(0), layout.footerY, footer.buttonWidth(0), FormScreenLayout.BUTTON_HEIGHT)
                .build(),
        )
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_DONE)) { onClose() }
                .dimensions(footer.buttonX(1), layout.footerY, footer.buttonWidth(1), FormScreenLayout.BUTTON_HEIGHT)
                .build(),
        )
        placeRows()
    }

    /** 最多可捲動的列數。 */
    private fun maximumScroll(): Int = if (layout.visibleFieldCount == 0) 0 else (rows.size - layout.visibleFieldCount).coerceAtLeast(0)

    /** 依捲動位置擺放每一列的選擇器，捲出可見範圍的列隱藏。 */
    private fun placeRows() {
        val layout = layout
        rows.forEachIndexed { index, row ->
            val selector = selectors[row] ?: return@forEachIndexed
            val visibleIndex = index - scrollIndex
            selector.visible = visibleIndex in 0 until layout.visibleFieldCount
            selector.x = layout.controlLeft
            selector.y = layout.fieldControlY(layout.fieldRowTop(visibleIndex.coerceAtLeast(0)))
            selector.width = layout.controlWidth
        }
    }

    /**
     * 展開或收起一列的選項清單。
     *
     * @param row 選擇器所在的列。
     * @param selector 選擇器按鈕。
     */
    private fun toggle(row: VisibilityRow, selector: RestartableMarqueeButtonWidget) {
        val options = options(row)
        dropdown.toggle(
            key = row,
            anchor = selector,
            options = options.map { DropdownOption(label = Component.translatable(it.translationKey)) },
            selectedIndex = options.indexOf(current(row)).takeIf { it >= 0 },
        ) { index -> select(row, options[index]) }
    }

    /**
     * 一列可選的顯示方式：人物有自動、顯示、半透明、隱藏；面板與桌上物件只有顯示、隱藏。
     *
     * @param row 有選擇器的列。
     * @return 依顯示順序的選項。
     */
    private fun options(row: VisibilityRow): List<HistoryReplayVisibilityOption> = when (row) {
        is VisibilityRow.Figure -> HistoryReplayVisibilityOption.entries
        else -> SHOWN_OR_HIDDEN
    }

    /**
     * 一列目前的顯示方式；視角所在那一家的人物與玩家資訊固定為隱藏。
     *
     * @param row 有選擇器的列。
     * @return 目前的顯示方式。
     */
    private fun current(row: VisibilityRow): HistoryReplayVisibilityOption = when (row) {
        is VisibilityRow.Figure -> if (isViewed(row.seat)) HistoryReplayVisibilityOption.HIDDEN else visibility.figure(row.seat.initialSeat)
        is VisibilityRow.PlayerInfo -> shownOrHidden(!isViewed(row.seat) && visibility.playerInfo(row.seat.initialSeat))
        VisibilityRow.RoundInfo -> shownOrHidden(visibility.roundInfo)
        VisibilityRow.TableProps -> shownOrHidden(visibility.tableProps)
        VisibilityRow.WinEffect -> shownOrHidden(visibility.winEffect)
        is VisibilityRow.Heading -> error("Heading rows have no selector")
    }

    /**
     * 套用一列選的顯示方式。
     *
     * @param row 有選擇器的列。
     * @param choice 選的顯示方式。
     */
    private fun select(row: VisibilityRow, choice: HistoryReplayVisibilityOption) {
        val shown = choice == HistoryReplayVisibilityOption.SHOWN
        when (row) {
            is VisibilityRow.Figure -> visibility.setFigure(row.seat.initialSeat, choice)
            is VisibilityRow.PlayerInfo -> visibility.setPlayerInfo(row.seat.initialSeat, shown)
            VisibilityRow.RoundInfo -> visibility.roundInfo = shown
            VisibilityRow.TableProps -> visibility.tableProps = shown
            VisibilityRow.WinEffect -> visibility.winEffect = shown
            is VisibilityRow.Heading -> Unit
        }
    }

    /**
     * 一家是否為目前視角所在的那一家。
     *
     * @param seat 座位。
     * @return 是時為 true。
     */
    private fun isViewed(seat: HistoryReplayVisibilityContext.Seat): Boolean = seat.initialSeat == context.viewedSeat

    /**
     * 一列的名稱。
     *
     * @param row 列。
     * @return 名稱。
     */
    private fun label(row: VisibilityRow): Component = when (row) {
        is VisibilityRow.Heading -> Component.translatable(row.translationKey)
        is VisibilityRow.Figure -> row.seat.label
        is VisibilityRow.PlayerInfo -> Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_PLAYER_INFO, row.seat.label)
        VisibilityRow.RoundInfo -> Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_ROUND_INFO)
        VisibilityRow.TableProps -> Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_TABLE_PROPS)
        VisibilityRow.WinEffect -> Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_WIN_EFFECT)
    }

    /**
     * 選擇器的提示：說明與全部選項；固定隱藏的列在其他選項上寫出原因。
     *
     * @param row 有選擇器的列。
     * @return 提示文字。
     */
    private fun tooltip(row: VisibilityRow): Component {
        val description = when (row) {
            is VisibilityRow.Figure -> MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_FIGURE_DESCRIPTION
            is VisibilityRow.PlayerInfo -> MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_PLAYER_INFO_DESCRIPTION
            VisibilityRow.RoundInfo -> MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_ROUND_INFO_DESCRIPTION
            VisibilityRow.TableProps -> MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_TABLE_PROPS_DESCRIPTION
            VisibilityRow.WinEffect -> MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_WIN_EFFECT_DESCRIPTION
            is VisibilityRow.Heading -> error("Heading rows have no selector")
        }
        val reason = lockedReason(row)
        val current = current(row)
        return ControlTooltip.options(
            description = Component.translatable(description),
            options = options(row).map { option ->
                ControlTooltip.Option(
                    label = Component.translatable(option.translationKey),
                    current = option == current,
                    unavailableReason = reason?.takeIf { option != current },
                )
            },
        )
    }

    /**
     * 固定隱藏的原因：視角所在那一家的人物與玩家資訊。
     *
     * @param row 列。
     * @return 原因；可以調整時為 null。
     */
    private fun lockedReason(row: VisibilityRow): Component? = when {
        row is VisibilityRow.Figure && isViewed(row.seat) -> Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_FIGURE_VIEWED)
        row is VisibilityRow.PlayerInfo && isViewed(row.seat) -> Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_PLAYER_INFO_VIEWED)
        else -> null
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        ScreenRendering.title(context, font, title, width)
        val layout = layout
        val viewText = Component.translatable(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_CURRENT_VIEW, this.context.viewLabel)
        context.drawCenteredString(font, viewText, width / 2, layout.headerTop, SUBTITLE_COLOR)
        var truncatedLabel: Component? = null
        rows.forEachIndexed { index, row ->
            val visibleIndex = index - scrollIndex
            if (visibleIndex !in 0 until layout.visibleFieldCount) return@forEachIndexed
            val rowTop = layout.fieldRowTop(visibleIndex)
            val heading = row is VisibilityRow.Heading
            if (!heading) layout.fieldStripe(visibleIndex).renderIfStriped(context, index)
            val color = when {
                heading -> HEADING_COLOR
                lockedReason(row) != null -> LOCKED_COLOR
                else -> LABEL_COLOR
            }
            // 選項列比段落標題縮排一點，與對局摘要相同。
            val inset = if (heading) 0 else ROW_INSET
            val labelWidth = (if (heading) layout.contentWidth else layout.labelWidth) - inset
            ScreenRendering.label(context, font, label(row), layout.contentLeft + inset, layout.fieldLabelY(rowTop), labelWidth, mouseX, mouseY, color)
                ?.let { truncatedLabel = it }
        }
        selectors.forEach { (row, selector) ->
            selector.message = Component.translatable(current(row).translationKey)
            selector.active = lockedReason(row) == null
            selector.tooltip = Tooltip.create(tooltip(row))
        }
        resetButton?.active = !visibility.isDefault
        VerticalScrollbar.render(context, layout.scrollbarLeft, layout.scrollbar(fieldCount = rows.size, scrollIndex = scrollIndex))
        super.render(context, dropdown.pointer(mouseX), dropdown.pointer(mouseY), delta)
        truncatedLabel?.takeUnless { dropdown.isOpen }?.let { context.renderTooltip(font, it, mouseX, mouseY) }
        dropdown.render(context, font, height, mouseX, mouseY)
    }

    /** 清單展開時在清單內捲動，否則逐列捲動。 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (dropdown.mouseScrolled(font, height, amount)) return true
        val layout = layout
        if (mouseY >= layout.fieldsTop && mouseY < layout.fieldsBottom && amount != 0.0) {
            scrollTo(scrollIndex + if (amount < 0) 1 else -1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    /**
     * 捲動到指定的第一個可見列。
     *
     * @param index 第一個可見列的索引。
     */
    private fun scrollTo(index: Int) {
        val next = index.coerceIn(0, maximumScroll())
        if (next == scrollIndex) return
        scrollIndex = next
        dropdown.close()
        placeRows()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dropdown.mouseClicked(font, height, mouseX, mouseY, button)) return true
        val layout = layout
        val scrollbar = layout.scrollbar(fieldCount = rows.size, scrollIndex = scrollIndex)
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollbar.maximumScroll > 0 && layout.isOverScrollbar(mouseX, mouseY)) {
            scrollDragging = true
            scrollGrabOffset = scrollbar.grabOffset(mouseY)
            scrollTo(scrollbar.scrollIndexFor(mouseY, scrollGrabOffset))
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /** 清單展開時不將拖曳傳遞至背景控制項；拖曳捲軸時逐列捲動。 */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (dropdown.isOpen) return true
        if (scrollDragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scrollTo(layout.scrollbar(fieldCount = rows.size, scrollIndex = scrollIndex).scrollIndexFor(mouseY, scrollGrabOffset))
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    /** 清單展開時不將放開滑鼠事件傳遞至背景控制項；放開左鍵時結束拖曳。 */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dropdown.isOpen) return true
        if (scrollDragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scrollDragging = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /** 下拉清單展開時不讓底層元件排入提示框。 */
    override fun setTooltipForNextRenderPass(lines: List<FormattedCharSequence>, positioner: ClientTooltipPositioner, focused: Boolean) {
        if (!dropdown.isOpen) super.setTooltipForNextRenderPass(lines, positioner, focused)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dropdown.keyPressed(font, height, keyCode)) return true
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false

    /** 完成或按 Esc 時回到回放畫面。 */
    override fun onClose() {
        session.backToReplay(replay)
    }

    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /** 可見性畫面的一列。 */
    private sealed interface VisibilityRow {
        /**
         * 段落標題。
         *
         * @property translationKey 標題的翻譯鍵。
         */
        data class Heading(val translationKey: String) : VisibilityRow

        /**
         * 一家的人物。
         *
         * @property seat 座位。
         */
        data class Figure(val seat: HistoryReplayVisibilityContext.Seat) : VisibilityRow

        /** 本局資訊。 */
        data object RoundInfo : VisibilityRow

        /**
         * 一家的玩家資訊。
         *
         * @property seat 座位。
         */
        data class PlayerInfo(val seat: HistoryReplayVisibilityContext.Seat) : VisibilityRow

        /** 桌上物件。 */
        data object TableProps : VisibilityRow

        /** 胡牌特效。 */
        data object WinEffect : VisibilityRow
    }

    /** 版面與顏色。 */
    private companion object {
        /** 面板與桌上物件可選的顯示方式。 */
        val SHOWN_OR_HIDDEN: List<HistoryReplayVisibilityOption> = listOf(HistoryReplayVisibilityOption.SHOWN, HistoryReplayVisibilityOption.HIDDEN)

        /**
         * 顯示與否對應的顯示方式。
         *
         * @param shown 是否顯示。
         * @return 顯示或隱藏。
         */
        fun shownOrHidden(shown: Boolean): HistoryReplayVisibilityOption = if (shown) HistoryReplayVisibilityOption.SHOWN else HistoryReplayVisibilityOption.HIDDEN

        /** 選項列名稱相對段落標題的縮排。 */
        const val ROW_INSET = 6

        /** 標題下目前視角那一行的高度。 */
        const val HEADER_HEIGHT = 14

        /** 目前視角文字的顏色。 */
        const val SUBTITLE_COLOR = 0xAAAAAA

        /** 段落標題的顏色。 */
        const val HEADING_COLOR = 0x8ED5DF

        /** 列名稱的顏色。 */
        const val LABEL_COLOR = 0xFFFFFF

        /** 固定隱藏的列名稱顏色。 */
        const val LOCKED_COLOR = 0xAAAAAA
    }
}
