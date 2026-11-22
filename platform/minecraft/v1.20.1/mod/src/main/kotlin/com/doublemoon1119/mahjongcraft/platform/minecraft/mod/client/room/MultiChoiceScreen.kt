package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.fitClientText
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelIcon
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.MultiChoiceLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.MultiChoiceSelection
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW

/**
 * 編輯單一多選欄位：一列一個選項，名稱靠左、按鈕外觀的勾選方塊靠右，整列都可以點。
 *
 * 勾選只修改這個畫面的暫存選取；[完成] 把選取寫回房間設定頁的草稿並返回，[取消] 與 Esc 放棄這次的勾選並返回。
 * 已選到上限時其他選項停用，提示框寫出原因；選取數量不在範圍內時 [完成] 停用。
 *
 * @property parent 返回時要顯示的房間設定頁。
 * @param fieldName 欄位名稱翻譯鍵。
 * @property fieldDescriptionKey 欄位說明翻譯鍵，指向標題時顯示。
 * @property editor 多選欄位的可選項目與數量限制。
 * @param initialSelection 開啟畫面時的選取快照。
 * @property onApply 按下完成時以選取結果呼叫；由呼叫端寫回草稿並返回。
 */
internal class MultiChoiceScreen(
    private val parent: Screen,
    fieldName: String,
    private val fieldDescriptionKey: String,
    private val editor: GameConfigEditorSpec.MultiChoice,
    initialSelection: Set<String>,
    private val onApply: (Set<String>) -> Unit,
) : Screen(Component.translatable(fieldName)) {
    /** 只在子畫面內修改的選取草稿。 */
    private val selected = initialSelection.toMutableSet()

    /** 選項清單的捲動位置。 */
    private val scroll = ScrollState()

    /** 可見選項與勾選方塊的對照。 */
    private val checkboxes = linkedMapOf<String, RestartableMarqueeButtonWidget>()

    /** 完成按鈕。 */
    private var doneButton: Button? = null

    /** 全選按鈕。 */
    private var selectAllButton: Button? = null

    override fun init() {
        clearWidgets()
        checkboxes.clear()
        scroll.clamp(layout().maximumScroll)
        val layout = layout()
        editor.optionIds.drop(scroll.index).take(layout.visibleCount).forEachIndexed { index, optionId ->
            val checkbox = RestartableMarqueeButtonWidget.builder(optionText(optionId)) { toggle(optionId) }
                .dimensions(layout.checkboxX, layout.rowTop(index), layout.checkboxSize, layout.checkboxSize)
                .build()
                .also { it.showsMessage = false }
            checkboxes[optionId] = addRenderableWidget(checkbox)
        }
        selectAllButton = footerButton(MultiChoiceLayout.SELECT_ALL, Component.translatable(MinecraftRoomScreenKeys.SELECT_ALL)) {
            if (MultiChoiceSelection.canSelectAll(editor)) {
                selected.clear()
                selected += editor.optionIds
                refreshSelectionControls()
            }
        }
        footerButton(MultiChoiceLayout.CLEAR, Component.translatable(MinecraftRoomScreenKeys.CLEAR_SELECTION)) {
            selected.clear()
            refreshSelectionControls()
        }
        footerButton(MultiChoiceLayout.CANCEL, CommonComponents.GUI_CANCEL) { onClose() }.also {
            it.tooltip = Tooltip.create(Component.translatable(MinecraftRoomScreenKeys.MULTI_CHOICE_CANCEL_HINT).withStyle(ChatFormatting.GRAY))
        }
        doneButton = footerButton(MultiChoiceLayout.DONE, Component.translatable(MinecraftRoomScreenKeys.DONE)) {
            if (MultiChoiceSelection.canApply(selected, editor)) onApply(selected.toSet())
        }
        refreshSelectionControls()
    }

    /**
     * 加入底部按鈕。
     *
     * @param index 按鈕順序。
     * @param label 按鈕文字。
     * @param action 按下後的行為。
     * @return 已加入畫面的按鈕。
     */
    private fun footerButton(index: Int, label: Component, action: () -> Unit): Button {
        val layout = layout()
        return addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(label) { action() }
                .dimensions(layout.footer.buttonX(index), layout.footerY, layout.footer.buttonWidth(index), BUTTON_HEIGHT)
                .build(),
        )
    }

    /**
     * 切換一個選項；已選到上限時未選的選項不處理。
     *
     * @param optionId 要切換的選項。
     */
    private fun toggle(optionId: String) {
        if (!canToggle(optionId)) return
        val next = MultiChoiceSelection.toggle(selected, optionId, editor)
        selected.clear()
        selected += next
        refreshSelectionControls()
    }

    /**
     * 判斷選項目前能否切換。
     *
     * @param optionId 選項。
     * @return 已勾選，或尚未選到上限時為 true。
     */
    private fun canToggle(optionId: String): Boolean = optionId in selected || selected.size < editor.maximumCount

    /** 依目前選取更新勾選方塊、全選與完成按鈕，不重建元件。 */
    private fun refreshSelectionControls() {
        checkboxes.forEach { (optionId, checkbox) ->
            checkbox.active = canToggle(optionId)
            checkbox.icon = PixelIcon.CHECK.takeIf { optionId in selected }
        }
        val canApply = MultiChoiceSelection.canApply(selected, editor)
        doneButton?.active = canApply
        doneButton?.tooltip = Tooltip.create(
            if (canApply) {
                Component.translatable(MinecraftRoomScreenKeys.MULTI_CHOICE_DONE_HINT).withStyle(ChatFormatting.GRAY)
            } else {
                Component.translatable(MinecraftRoomScreenKeys.SELECTION_OUT_OF_RANGE, editor.minimumCount, editor.maximumCount).withStyle(ChatFormatting.RED)
            },
        )
        val canSelectAll = MultiChoiceSelection.canSelectAll(editor)
        selectAllButton?.active = canSelectAll
        selectAllButton?.tooltip = if (canSelectAll) null else Tooltip.create(Component.translatable(MinecraftRoomScreenKeys.SELECT_ALL_UNAVAILABLE, editor.maximumCount).withStyle(ChatFormatting.RED))
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val layout = layout()
        if ((layout.optionAt(mouseX, mouseY) != null || layout.isOverScrollbar(mouseX, mouseY)) && scroll.scrollBy(amount, layout.maximumScroll)) {
            init()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val layout = layout()
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout.isOverScrollbar(mouseX, mouseY)) {
            if (scroll.beginDrag(mouseY, layout.scrollbar)) init()
            return true
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        val index = layout.optionAt(mouseX, mouseY)
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && index != null) {
            val checkbox = checkboxes[editor.optionIds[index]]
            if (checkbox?.active == true) {
                minecraft?.soundManager?.let(checkbox::playDownSound)
                checkbox.onPress()
                setFocused(checkbox)
                return true
            }
        }
        return false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scroll.dragging) {
            if (scroll.dragTo(mouseY, layout().scrollbar)) init()
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scroll.dragging
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) scroll.endDrag()
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    /** 放棄這次的勾選並返回房間設定頁。 */
    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val layout = layout()
        ScreenRendering.title(context, font, fitClientText(font, title, layout.contentWidth), width)
        context.drawCenteredString(
            font,
            fitClientText(
                textRenderer = font,
                text = Component.translatable(MinecraftRoomScreenKeys.SELECTION_LIMITS, selected.size, editor.minimumCount, editor.maximumCount),
                maximumWidth = layout.contentWidth,
            ),
            width / 2,
            layout.countY,
            COUNT_COLOR,
        )
        val hoveredIndex = layout.optionAt(mouseX.toDouble(), mouseY.toDouble())
        editor.optionIds.drop(scroll.index).take(layout.visibleCount).forEachIndexed { index, optionId ->
            val area = layout.rowArea(index)
            area.renderIfStriped(context, scroll.index + index)
            val checkbox = checkboxes[optionId]
            val hovered = hoveredIndex == scroll.index + index
            if (hovered && checkbox?.active == true) context.fill(area.left, area.top, area.right, area.bottom, HOVER_COLOR)
            if (checkbox != null) checkbox.outlined = hovered && checkbox.active
            context.drawString(
                font,
                fitClientText(font, optionText(optionId), layout.labelWidth),
                layout.contentLeft,
                layout.rowTop(index) + (BUTTON_HEIGHT - VISIBLE_TEXT_HEIGHT) / 2,
                if (checkbox?.active == true) LABEL_COLOR else DISABLED_LABEL_COLOR,
            )
        }
        val bar = layout.scrollbar
        if (bar.maximumScroll > 0) VerticalScrollbar.render(context, layout.scrollbarX, bar)
        super.render(context, mouseX, mouseY, delta)
        if (hoveredIndex != null) {
            optionTooltip(editor.optionIds[hoveredIndex], layout)?.let { context.renderTooltip(font, it, mouseX, mouseY) }
        } else if (mouseX in layout.contentLeft until layout.contentRight && mouseY in TITLE_HOVER_TOP until layout.rowsTop - 2) {
            context.renderTooltip(font, wrap(listOf(Component.translatable(fieldDescriptionKey).withStyle(ChatFormatting.GRAY)), layout.contentWidth), mouseX, mouseY)
        }
    }

    /**
     * 建立選項的提示：名稱被截斷時的完整名稱、選項說明，以及停用時的原因。
     *
     * @param optionId 選項。
     * @param layout 目前版面。
     * @return 提示的每一行；沒有內容時為 null。
     */
    private fun optionTooltip(optionId: String, layout: MultiChoiceLayout): List<FormattedCharSequence>? {
        val name = optionText(optionId)
        val lines = buildList {
            if (font.width(name) > layout.labelWidth) add(name.copy().withStyle(ChatFormatting.WHITE))
            editor.descriptionTranslationKeyFor(optionId)?.let { add(Component.translatable(it).withStyle(ChatFormatting.GRAY)) }
            if (!canToggle(optionId)) {
                if (isNotEmpty()) add(Component.empty())
                add(Component.translatable(MinecraftRoomScreenKeys.OPTION_AT_MAXIMUM, editor.maximumCount).withStyle(ChatFormatting.RED))
            }
        }
        return lines.takeIf { it.isNotEmpty() }?.let { wrap(it, layout.contentWidth) }
    }

    /**
     * 將提示的每一行依寬度換行。
     *
     * @param lines 提示的每一行。
     * @param maximumWidth 提示可用的寬度。
     * @return 換行後的提示行。
     */
    private fun wrap(lines: List<Component>, maximumWidth: Int): List<FormattedCharSequence> = lines.flatMap { line ->
        if (line.string.isEmpty()) listOf(line.visualOrderText) else font.split(line, maximumWidth)
    }

    /** 建立所有繪製與輸入共用的版面。 */
    private fun layout(): MultiChoiceLayout = MultiChoiceLayout(width, height, editor.optionIds.size, scroll.index)

    /** 取得名稱的明確翻譯，未登記時保留完整 ID。 */
    private fun optionText(optionId: String): Component = editor.translationKeyFor(optionId)?.let(Component::translatable) ?: Component.literal(optionId)

    /** 尺寸與顏色。 */
    private companion object {
        /** 按鈕高度。 */
        const val BUTTON_HEIGHT = 20

        /** 垂直置中文字使用的可見字高。 */
        const val VISIBLE_TEXT_HEIGHT = 8

        /** 指向標題時顯示欄位說明的範圍上界。 */
        const val TITLE_HOVER_TOP = 4

        /** 已選數量文字的顏色。 */
        const val COUNT_COLOR = 0xAAAAAA

        /** 可切換選項的名稱顏色。 */
        const val LABEL_COLOR = 0xFFFFFF

        /** 停用選項的名稱顏色。 */
        const val DISABLED_LABEL_COLOR = 0x8A8A8A

        /** 游標停留列的加亮底色。 */
        const val HOVER_COLOR = 0x1FFFFFFF
    }
}
