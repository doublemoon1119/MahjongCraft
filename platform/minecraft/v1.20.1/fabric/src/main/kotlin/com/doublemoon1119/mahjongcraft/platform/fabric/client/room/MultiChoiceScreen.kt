package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.fabric.client.fitClientText
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.CheckboxWidget
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 以左側名稱與右側填色勾選框編輯單一多選欄位；套用前的選取不會修改房間草稿。
 *
 * @property parent 返回時要顯示的房間設定頁。
 * @param fieldName 欄位名稱翻譯鍵。
 * @property fieldDescriptionKey 欄位說明翻譯鍵，指向標題時顯示。
 * @property editor 多選欄位的可選項目與數量限制。
 * @param initialSelection 開啟畫面時的選取快照。
 * @property onApply 套用前端選取結果的回呼。
 */
internal class MultiChoiceScreen(
    private val parent: Screen,
    fieldName: String,
    private val fieldDescriptionKey: String,
    private val editor: GameConfigEditorSpec.MultiChoice,
    initialSelection: Set<String>,
    private val onApply: (Set<String>) -> Unit,
) : Screen(Text.translatable(fieldName)) {
    /** 只在子畫面內修改的選取草稿。 */
    private val selected = initialSelection.toMutableSet()

    /** 選項清單的捲動位置。 */
    private val scroll = ScrollState()

    /** 可見選項與勾選框的對照，供整列點擊及更新啟用狀態。 */
    private val checkboxes = linkedMapOf<String, CheckboxWidget>()

    /** 依合法選取狀態更新的套用按鈕。 */
    private var applyButton: ButtonWidget? = null

    override fun init() {
        clearChildren()
        checkboxes.clear()
        scroll.clamp(layout().maximumScroll)
        val layout = layout()
        editor.optionIds.drop(scroll.index).take(layout.visibleCount).forEachIndexed { index, optionId ->
            val checkbox = object : CheckboxWidget(
                layout.checkboxX,
                layout.rowsTop + index * layout.rowHeight + 2,
                layout.checkboxSize,
                layout.checkboxSize,
                optionText(optionId),
                optionId in selected,
                false,
            ) {
                /** 以小型細框及內側填色顯示選取狀態；原版元件保留點擊、鍵盤與朗讀行為。 */
                override fun renderButton(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
                    val size = 12
                    val left = x + (width - size) / 2
                    val top = y + (height - size) / 2
                    val hoveredOption = layout().optionAt(mouseX.toDouble(), mouseY.toDouble())
                    val rowHovered = hoveredOption != null && editor.optionIds[hoveredOption] == optionId
                    val borderColor = when {
                        !active -> 0xFF606060.toInt()
                        rowHovered || isFocused -> 0xFFE8FFFF.toInt()
                        isChecked -> 0xFF8ED5DF.toInt()
                        else -> 0xFFB0B0B0.toInt()
                    }
                    context.fill(left, top, left + size, top + size, 0xCC111820.toInt())
                    context.drawBorder(left, top, size, size, borderColor)
                    if (isChecked) {
                        context.fill(
                            left + 3,
                            top + 3,
                            left + size - 3,
                            top + size - 3,
                            if (active) 0xFF8ED5DF.toInt() else 0xFF606060.toInt(),
                        )
                    }
                }

                override fun onPress() {
                    if (!active) return
                    super.onPress()
                    val next = MultiChoiceSelection.toggle(selected, optionId, editor)
                    selected.clear()
                    selected += next
                    refreshSelectionControls()
                }
            }
            checkboxes[optionId] = addDrawableChild(checkbox)
        }
        applyButton = addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.APPLY)) {
                if (MultiChoiceSelection.canApply(selected, editor)) onApply(selected.toSet())
            }.dimensions(layout.applyX, layout.footerY, layout.buttonWidth, 20).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(ScreenTexts.CANCEL) { close() }
                .dimensions(layout.cancelX, layout.footerY, layout.buttonWidth, 20).build(),
        )
        refreshSelectionControls()
    }

    /** 更新數量限制，不重建元件，保留勾選框的鍵盤焦點。 */
    private fun refreshSelectionControls() {
        checkboxes.forEach { (optionId, checkbox) ->
            checkbox.active = optionId in selected || selected.size < editor.maximumCount
        }
        applyButton?.active = MultiChoiceSelection.canApply(selected, editor)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val layout = layout()
        if (mouseX >= layout.left &&
            mouseX < layout.right &&
            mouseY >= layout.rowsTop &&
            mouseY < layout.scrollbar.trackBottom &&
            scroll.scrollBy(amount, layout.maximumScroll)
        ) {
            init()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val layout = layout()
        if (button == 0 && layout.isOverScrollbar(mouseX, mouseY)) {
            if (scroll.beginDrag(mouseY, layout.scrollbar)) init()
            return true
        }
        val index = layout.optionAt(mouseX, mouseY)
        if (button == 0 && index != null && mouseX < layout.checkboxX) {
            val checkbox = checkboxes[editor.optionIds[index]]
            if (checkbox?.active == true) {
                client?.soundManager?.let(checkbox::playDownSound)
                checkbox.onPress()
                setFocused(checkbox)
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (button == 0 && scroll.dragging) {
            if (scroll.dragTo(mouseY, layout().scrollbar)) init()
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = button == 0 && scroll.dragging
        if (button == 0) scroll.endDrag()
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val layout = layout()
        context.drawCenteredTextWithShadow(textRenderer, fitClientText(textRenderer, title, layout.panelWidth), width / 2, 15, 0xFFFFFF)
        context.drawCenteredTextWithShadow(
            textRenderer,
            fitClientText(
                textRenderer,
                Text.translatable(MinecraftRoomScreenKeys.SELECTION_LIMITS, selected.size, editor.minimumCount, editor.maximumCount),
                layout.panelWidth,
            ),
            width / 2,
            31,
            0xD0D0D0,
        )
        val hoveredIndex = layout.optionAt(mouseX.toDouble(), mouseY.toDouble())
        editor.optionIds.drop(scroll.index).take(layout.visibleCount).forEachIndexed { index, optionId ->
            val rowY = layout.rowsTop + index * layout.rowHeight
            val checkbox = checkboxes[optionId]
            if (hoveredIndex == scroll.index + index || checkbox?.isFocused == true) {
                context.fill(layout.labelX - 4, rowY, layout.rowRight + 4, rowY + layout.rowHeight, 0x20FFFFFF)
            }
            context.drawTextWithShadow(
                textRenderer,
                fitClientText(textRenderer, optionText(optionId), layout.labelWidth),
                layout.labelX,
                rowY + (layout.rowHeight - textRenderer.fontHeight) / 2,
                if (checkbox?.active == true) 0xFFFFFF else 0xA0A0A0,
            )
        }
        val bar = layout.scrollbar
        if (bar.maximumScroll > 0) {
            context.fill(layout.scrollbarX, bar.trackTop, layout.right, bar.trackBottom, 0x80505050.toInt())
            context.fill(layout.scrollbarX, bar.thumbTop, layout.right, bar.thumbTop + bar.thumbHeight, 0xFFD0D0D0.toInt())
        }
        super.render(context, mouseX, mouseY, delta)
        if (hoveredIndex != null) {
            val optionId = editor.optionIds[hoveredIndex]
            val name = optionText(optionId)
            val description = editor.descriptionTranslationKeyFor(optionId)
            if (description != null || textRenderer.getWidth(name) > layout.labelWidth) {
                context.drawOrderedTooltip(textRenderer, tooltip(name, description, layout.panelWidth), mouseX, mouseY)
            }
        } else if (mouseX in layout.left until layout.right && mouseY in 12..42) {
            context.drawOrderedTooltip(textRenderer, tooltip(title, fieldDescriptionKey, layout.panelWidth), mouseX, mouseY)
        }
    }

    /** 建立所有繪製與輸入共用的窄版幾何。 */
    private fun layout(): MultiChoiceLayout = MultiChoiceLayout(width, height, editor.optionIds.size, scroll.index)

    /** 取得名稱的明確翻譯，未登記時保留完整 ID。 */
    private fun optionText(optionId: String): Text = editor.translationKeyFor(optionId)?.let(Text::translatable) ?: Text.literal(optionId)

    /**
     * 將名稱與可選說明依字寬換行，避免懸浮文字超出窄視窗。
     *
     * @param name 完整選項或欄位名稱。
     * @param descriptionKey 可選的說明翻譯鍵。
     * @param maximumWidth 懸浮文字可用的寬度。
     * @return 保留完整名稱與說明的懸浮文字行。
     */
    private fun tooltip(name: Text, descriptionKey: String?, maximumWidth: Int): List<OrderedText> = buildList {
        addAll(textRenderer.wrapLines(name.copy().formatted(Formatting.GOLD), maximumWidth))
        if (descriptionKey != null) {
            addAll(textRenderer.wrapLines(Text.translatable(descriptionKey).formatted(Formatting.GRAY), maximumWidth))
        }
    }
}
