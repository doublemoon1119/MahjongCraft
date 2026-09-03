package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.SettingsFooterLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.math.roundToInt

/** 原生 HUD 位置編輯器；拖曳只修改草稿，套用時才交由父設定畫面原子保存。 */
class MahjongHudLayoutEditorScreen(
    private val parent: MahjongClientConfigScreen,
    initialLayout: MahjongHudLayoutConfig,
) : Screen(Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_TITLE)) {
    /** 最近一次成功套用的配置。 */
    private var baseline = initialLayout

    /** 目前拖曳中的配置草稿。 */
    private var draft = initialLayout

    /** 目前被拖曳的 HUD 區塊。 */
    private var dragging: HudElement? = null

    /** 拖曳起點相對 HUD 左上角的 X。 */
    private var dragOffsetX = 0.0

    /** 拖曳起點相對 HUD 左上角的 Y。 */
    private var dragOffsetY = 0.0

    /** 最近一次保存是否失敗。 */
    private var saveFailed = false

    /** 目前操作面板使用的尺寸預覽情境。 */
    private var scenario = PreviewScenario.CALL

    /** 目前取得完整預覽與拖曳焦點的 HUD。 */
    private var selectedElement = HudElement.DECISION

    /** 所有未選取 HUD 共用的預覽方式。 */
    private var otherHudVisibility = PreviewVisibility.HIDDEN

    /** 玩家是否手動隱藏所有 editor 控制項。 */
    private var controlsManuallyHidden = false

    /** 頂部水平工具列按鈕及其未捲動內容座標。 */
    private val toolbarEntries = mutableListOf<ToolbarEntry>()

    /** 固定在工具列右側、不參與水平捲動的控制項隱藏按鈕。 */
    private var hideControlsButton: ButtonWidget? = null

    /** 固定在隱藏按鈕左側的其他 HUD 預覽選單按鈕。 */
    private var otherHudPreviewButton: ButtonWidget? = null

    /** 目前展開的下拉選單。 */
    private var openDropdown: DropdownKind? = null

    /** 頂部工具列目前水平捲動量。 */
    private var toolbarScroll = 0.0

    /** 是否正在拖曳工具列 scrollbar。 */
    private var draggingToolbarScrollbar = false

    /** 工具列 scrollbar 拖曳起點的游標 X。 */
    private var toolbarDragStartX = 0.0

    /** 工具列 scrollbar 拖曳起點的捲動量。 */
    private var toolbarDragStartScroll = 0.0

    /** 套用按鈕。 */
    private var applyButton: ButtonWidget? = null

    /** 復原按鈕。 */
    private var undoButton: ButtonWidget? = null

    /** 重設配置按鈕。 */
    private var resetButton: ButtonWidget? = null

    /** Editor 不暫停單人遊戲或 integrated server。 */
    override fun shouldPause(): Boolean = false

    /** 建立固定單列的重設、復原、套用與返回按鈕。 */
    override fun init() {
        toolbarEntries.clear()
        openDropdown = null
        val footer = SettingsFooterLayout.create(12, width - 24, 104, 6)
        val y = height - 28
        addDropdownButtons()
        otherHudPreviewButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(dropdownButtonText(DropdownKind.VISIBILITY)) {
                openDropdown = if (openDropdown == DropdownKind.VISIBILITY) null else DropdownKind.VISIBILITY
            }.dimensions(
                width - TOOLBAR_MARGIN - HIDE_CONTROLS_WIDTH - TOOLBAR_GAP - OTHER_PREVIEW_WIDTH,
                TOOLBAR_TOP,
                OTHER_PREVIEW_WIDTH,
                20,
            ).build(),
        )
        hideControlsButton = addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_HIDE_CONTROLS)) {
                controlsManuallyHidden = true
                openDropdown = null
                updateControlVisibility()
            }.dimensions(width - TOOLBAR_MARGIN - HIDE_CONTROLS_WIDTH, TOOLBAR_TOP, HIDE_CONTROLS_WIDTH, 20).build(),
        )
        resetButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_RESET)) {
                draft = MahjongHudLayoutConfig()
                saveFailed = false
                refreshButtons()
            }.dimensions(footer.resetX, y, footer.resetWidth, 20).build(),
        )
        undoButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.UNDO)) {
                draft = baseline
                saveFailed = false
                refreshButtons()
            }.dimensions(footer.undoX, y, footer.actionWidth, 20).build(),
        )
        applyButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.APPLY)) {
                apply(returnAfterSave = false)
            }.dimensions(footer.applyX, y, footer.actionWidth, 20).build(),
        )
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.BACK)) {
                requestReturn()
            }.dimensions(footer.doneX, y, footer.actionWidth, 20).build(),
        )
        refreshButtons()
        toolbarScroll = toolbarScroll.coerceIn(0.0, maximumToolbarScroll())
        updateToolbarPositions()
        updateControlVisibility()
    }

    /** 建立 HUD、預覽模式與操作情境三個下拉選單按鈕。 */
    private fun addDropdownButtons() {
        DropdownKind.entries.forEach { kind ->
            if (kind == DropdownKind.VISIBILITY) return@forEach
            if (kind == DropdownKind.SCENARIO && selectedElement != HudElement.DECISION) return@forEach
            addToolbarButton(
                RestartableMarqueeButtonWidget.builder(dropdownButtonText(kind)) {
                    openDropdown = if (openDropdown == kind) null else kind
                }.dimensions(0, TOOLBAR_TOP, DROPDOWN_WIDTH, 20).build(),
                kind,
            )
        }
    }

    /** Esc 使用與返回按鈕相同的未保存變更保護。 */
    override fun close() {
        if (controlsManuallyHidden) {
            controlsManuallyHidden = false
            updateControlVisibility()
        } else {
            requestReturn()
        }
    }

    /** 繪製背景、參考線與三個實際可拖曳的 HUD 預覽。 */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, SCREEN_OVERLAY_COLOR)
        context.fill(width / 2, 24, width / 2 + 1, height - 34, GUIDE_COLOR)
        context.fill(0, height / 2, width, height / 2 + 1, GUIDE_COLOR)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, TITLE_COLOR)
        HudElement.entries.forEach { element ->
            if (element == selectedElement) {
                renderPreview(context, element, bounds(element), mouseX, mouseY)
            } else {
                when (otherHudVisibility) {
                    PreviewVisibility.OUTLINE -> renderOutline(context, element, bounds(element))
                    PreviewVisibility.HIDDEN -> Unit
                }
            }
        }
        if (saveFailed) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable(MinecraftClientConfigScreenKeys.SAVE_FAILED).formatted(Formatting.RED),
                width / 2,
                height - 40,
                0xFFFFFF,
            )
        }
        if (controlsVisible()) {
            context.matrices.push()
            context.matrices.translate(0.0, 0.0, EDITOR_CONTROLS_Z)
            renderFixedControls(context, mouseX, mouseY, delta)
            renderToolbar(context, mouseX, mouseY, delta)
            context.matrices.pop()
        } else if (controlsManuallyHidden && dragging == null) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SHOW_CONTROLS_HINT),
                width / 2,
                height - 12,
                0xB0B0B0,
            )
        }
    }

    /** 左鍵按住任一預覽框時開始拖曳。 */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (controlsVisible() && button == 0 && hasToolbarOverflow() && isOverToolbarScrollbar(mouseX, mouseY)) {
            val thumb = toolbarScrollbarThumb()
            if (mouseX !in thumb.left.toDouble()..thumb.right.toDouble()) {
                toolbarScroll = toolbarScrollbarFromThumb(mouseX - thumb.width / 2.0)
                updateToolbarPositions()
            }
            draggingToolbarScrollbar = true
            toolbarDragStartX = mouseX
            toolbarDragStartScroll = toolbarScroll
            return true
        }
        if (controlsVisible() && button == 0 && handleDropdownClick(mouseX, mouseY)) return true
        if (
            controlsVisible() &&
            listOfNotNull(otherHudPreviewButton, hideControlsButton).any { it.mouseClicked(mouseX, mouseY, button) }
        ) {
            return true
        }
        if (controlsVisible() && isInsideToolbar(mouseX, mouseY)) {
            val handled = toolbarEntries.any { it.button.visible && it.button.mouseClicked(mouseX, mouseY, button) }
            if (!handled) openDropdown = null
            return handled
        }
        openDropdown = null
        if (controlsVisible() && mouseY >= TOOLBAR_TOP && mouseY < TOOLBAR_BOTTOM) return false
        if (controlsVisible() && super.mouseClicked(mouseX, mouseY, button)) return true
        if (button == 0) {
            val candidates = buildList {
                add(selectedElement)
                HudElement.entries.filterTo(this) {
                    it != selectedElement && otherHudVisibility != PreviewVisibility.HIDDEN
                }
            }
            candidates.firstOrNull { bounds(it).contains(mouseX, mouseY) }?.let { element ->
                val bounds = bounds(element)
                selectedElement = element
                dragging = element
                dragOffsetX = mouseX - bounds.left
                dragOffsetY = mouseY - bounds.top
                updateControlVisibility()
                return true
            }
        }
        return false
    }

    /** 依 HUD 可調整軸更新比例，並由比例座標自然限制完整 bounds 在畫面內。 */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (draggingToolbarScrollbar && button == 0) {
            val thumb = toolbarScrollbarThumb()
            val travel = (toolbarViewportWidth() - thumb.width).coerceAtLeast(1)
            toolbarScroll = (toolbarDragStartScroll + (mouseX - toolbarDragStartX) / travel * maximumToolbarScroll())
                .coerceIn(0.0, maximumToolbarScroll())
            updateToolbarPositions()
            return true
        }
        val element = dragging ?: return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        if (button != 0) return false
        val size = previewSize(element)
        val xRatio = hudRatio((mouseX - dragOffsetX).roundToInt(), width, size.first)
        val yRatio = hudRatio((mouseY - dragOffsetY).roundToInt(), height, size.second)
        draft = when (element) {
            HudElement.DECISION -> draft.copy(decisionPanelY = snap(yRatio))
            HudElement.COMPACT -> draft.copy(compactPromptX = snap(xRatio), compactPromptY = snap(yRatio))
            HudElement.ANALYSIS -> draft.copy(discardAnalysisY = snap(yRatio))
        }
        saveFailed = false
        refreshButtons()
        return true
    }

    /** 放開左鍵後結束拖曳。 */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && draggingToolbarScrollbar) {
            draggingToolbarScrollbar = false
            return true
        }
        if (button == 0 && dragging != null) {
            dragging = null
            clearAndInit()
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /** 工具列範圍內的滾輪輸入轉為水平捲動。 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (controlsVisible() && hasToolbarOverflow() && isInsideToolbarArea(mouseX, mouseY)) {
            toolbarScroll = (toolbarScroll - amount * TOOLBAR_SCROLL_STEP).coerceIn(0.0, maximumToolbarScroll())
            updateToolbarPositions()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    /** 透過父畫面的完整草稿保存流程套用 HUD 配置。 */
    private fun apply(returnAfterSave: Boolean) {
        if (draft == baseline) {
            if (returnAfterSave) client?.setScreen(parent)
            return
        }
        if (parent.applyHudLayout(draft)) {
            baseline = draft
            saveFailed = false
            refreshButtons()
            if (returnAfterSave) client?.setScreen(parent)
        } else {
            saveFailed = true
        }
    }

    /** 無變更時直接返回；有變更時開啟三選項確認畫面。 */
    private fun requestReturn() {
        if (draft == baseline) {
            client?.setScreen(parent)
        } else {
            client?.setScreen(HudLayoutUnsavedChangesScreen(this, ::apply, { client?.setScreen(parent) }))
        }
    }

    /** 依目前草稿更新按鈕狀態與差異 tooltip。 */
    private fun refreshButtons() {
        val tooltip = if (draft == baseline) {
            null
        } else {
            Tooltip.of(
                clientConfigDifferenceText(
                    MahjongClientConfigState(hudLayout = baseline),
                    MahjongClientConfigState(hudLayout = draft),
                ),
            )
        }
        applyButton?.active = draft != baseline
        undoButton?.active = draft != baseline
        resetButton?.active = draft != MahjongHudLayoutConfig()
        applyButton?.tooltip = tooltip
        undoButton?.tooltip = tooltip
    }

    /** 依手動隱藏、拖曳狀態與目前焦點同步全部原版 widget 的顯示狀態。 */
    private fun updateControlVisibility() {
        val visible = controlsVisible()
        children().filterIsInstance<ButtonWidget>().forEach { it.visible = visible }
    }

    /** 將按鈕加入水平工具列並配置下一個內容座標。 */
    private fun <T : ButtonWidget> addToolbarButton(button: T, kind: DropdownKind): T {
        val contentX = toolbarEntries.lastOrNull()?.let { it.contentX + it.button.width + TOOLBAR_GAP } ?: 0
        toolbarEntries += ToolbarEntry(button, contentX, kind)
        return addDrawableChild(button)
    }

    /** 只透過 Screen 預設流程繪製底部固定按鈕與其 tooltip。 */
    private fun renderFixedControls(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        toolbarEntries.forEach { it.button.visible = false }
        children().filterIsInstance<ButtonWidget>().filter { it.visible }.forEach { button ->
            context.fill(button.x, button.y, button.x + button.width, button.y + button.height, EDITOR_BUTTON_BACKING_COLOR)
        }
        super.render(context, mouseX, mouseY, delta)
        toolbarEntries.forEach { it.button.visible = true }
    }

    /** 在裁切 viewport 中繪製工具列，溢出時於下方繪製滿寬 scrollbar。 */
    private fun renderToolbar(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        updateToolbarPositions()
        context.enableScissor(TOOLBAR_MARGIN, TOOLBAR_TOP, toolbarViewportRight(), TOOLBAR_BOTTOM)
        toolbarEntries.forEach { entry ->
            if (entry.button.visible) {
                context.fill(
                    entry.button.x,
                    entry.button.y,
                    entry.button.x + entry.button.width,
                    entry.button.y + entry.button.height,
                    EDITOR_BUTTON_BACKING_COLOR,
                )
                entry.button.render(context, mouseX, mouseY, delta)
            }
        }
        context.disableScissor()
        if (hasToolbarOverflow()) {
            context.fill(
                TOOLBAR_MARGIN,
                TOOLBAR_SCROLLBAR_TOP,
                toolbarViewportRight(),
                TOOLBAR_SCROLLBAR_TOP + TOOLBAR_SCROLLBAR_HEIGHT,
                TOOLBAR_TRACK_COLOR,
            )
            val thumb = toolbarScrollbarThumb()
            context.fill(thumb.left, TOOLBAR_SCROLLBAR_TOP, thumb.right, TOOLBAR_SCROLLBAR_TOP + TOOLBAR_SCROLLBAR_HEIGHT, TOOLBAR_THUMB_COLOR)
        }
        context.matrices.push()
        context.matrices.translate(0.0, 0.0, DROPDOWN_Z_OFFSET)
        renderDropdown(context, mouseX, mouseY)
        context.matrices.pop()
    }

    /** 繪製目前展開的下拉選單；popup 不受工具列水平裁切影響。 */
    private fun renderDropdown(context: DrawContext, mouseX: Int, mouseY: Int) {
        val kind = openDropdown ?: return
        val anchor = dropdownAnchor(kind) ?: return
        val options = dropdownOptions(kind)
        val left = anchor.x.coerceIn(TOOLBAR_MARGIN, (width - TOOLBAR_MARGIN - DROPDOWN_POPUP_WIDTH).coerceAtLeast(TOOLBAR_MARGIN))
        val top = TOOLBAR_POPUP_TOP
        val bottom = top + options.size * DROPDOWN_OPTION_HEIGHT
        context.fill(left - 1, top - 1, left + DROPDOWN_POPUP_WIDTH + 1, bottom + 1, DROPDOWN_BORDER_COLOR)
        options.forEachIndexed { index, option ->
            val optionTop = top + index * DROPDOWN_OPTION_HEIGHT
            val hovered = mouseX in left until left + DROPDOWN_POPUP_WIDTH && mouseY in optionTop until optionTop + DROPDOWN_OPTION_HEIGHT
            context.fill(
                left,
                optionTop,
                left + DROPDOWN_POPUP_WIDTH,
                optionTop + DROPDOWN_OPTION_HEIGHT,
                if (hovered) DROPDOWN_HOVER_COLOR else DROPDOWN_BACKGROUND_COLOR,
            )
            context.drawTextWithShadow(textRenderer, option.label, left + 6, optionTop + 6, if (option.selected) TITLE_COLOR else 0xFFFFFF)
        }
    }

    /** 若游標點中展開選單的項目便套用選擇。 */
    private fun handleDropdownClick(mouseX: Double, mouseY: Double): Boolean {
        val kind = openDropdown ?: return false
        val anchor = dropdownAnchor(kind) ?: return false
        val options = dropdownOptions(kind)
        val left = anchor.x.coerceIn(TOOLBAR_MARGIN, (width - TOOLBAR_MARGIN - DROPDOWN_POPUP_WIDTH).coerceAtLeast(TOOLBAR_MARGIN))
        if (mouseX < left || mouseX >= left + DROPDOWN_POPUP_WIDTH || mouseY < TOOLBAR_POPUP_TOP) return false
        val index = ((mouseY - TOOLBAR_POPUP_TOP) / DROPDOWN_OPTION_HEIGHT).toInt()
        val option = options.getOrNull(index) ?: return false
        option.select()
        openDropdown = null
        clearAndInit()
        return true
    }

    /** 取得指定下拉選單目前可選項目與穩定順序。 */
    private fun dropdownOptions(kind: DropdownKind): List<DropdownOption> = when (kind) {
        DropdownKind.HUD -> HudElement.entries.map { element ->
            DropdownOption(Text.translatable(element.translationKey), element == selectedElement) {
                selectedElement = element
            }
        }
        DropdownKind.VISIBILITY -> PreviewVisibility.entries.map { visibility ->
            DropdownOption(Text.translatable(visibility.translationKey), otherHudVisibility == visibility) {
                otherHudVisibility = visibility
            }
        }
        DropdownKind.SCENARIO -> PreviewScenario.entries.map { preview ->
            DropdownOption(Text.translatable(preview.translationKey), scenario == preview) { scenario = preview }
        }
    }

    /** 組合下拉選單欄位名稱、目前值與展開符號。 */
    private fun dropdownButtonText(kind: DropdownKind): Text = Text.translatable(
        kind.translationKey,
        when (kind) {
            DropdownKind.HUD -> Text.translatable(selectedElement.translationKey)
            DropdownKind.VISIBILITY -> Text.translatable(otherHudVisibility.translationKey)
            DropdownKind.SCENARIO -> Text.translatable(scenario.translationKey)
        },
    )

    /** 取得下拉選單按鈕，不依工具列內容順序推斷控制對象。 */
    private fun dropdownAnchor(kind: DropdownKind): ButtonWidget? = when (kind) {
        DropdownKind.VISIBILITY -> otherHudPreviewButton
        else -> toolbarEntries.firstOrNull { it.kind == kind }?.button
    }

    /** 依目前捲動量更新工具列按鈕實際畫面位置。 */
    private fun updateToolbarPositions() {
        val contentOffset = if (hasToolbarOverflow()) {
            TOOLBAR_MARGIN - toolbarScroll.toInt()
        } else {
            TOOLBAR_MARGIN + (toolbarViewportWidth() - toolbarContentWidth()) / 2
        }
        toolbarEntries.forEach { entry -> entry.button.x = contentOffset + entry.contentX }
    }

    /** 工具列全部按鈕所需內容寬度。 */
    private fun toolbarContentWidth(): Int = toolbarEntries.lastOrNull()?.let { it.contentX + it.button.width } ?: 0

    /** 工具列可見寬度。 */
    private fun toolbarViewportWidth(): Int = (toolbarViewportRight() - TOOLBAR_MARGIN).coerceAtLeast(1)

    /** 工具列捲動 viewport 的右邊界，保留固定隱藏按鈕空間。 */
    private fun toolbarViewportRight(): Int = (
        width - TOOLBAR_MARGIN * 2 - HIDE_CONTROLS_WIDTH - OTHER_PREVIEW_WIDTH - TOOLBAR_GAP
        ).coerceAtLeast(TOOLBAR_MARGIN + 1)

    /** 工具列最大水平捲動量。 */
    private fun maximumToolbarScroll(): Double = (toolbarContentWidth() - toolbarViewportWidth()).coerceAtLeast(0).toDouble()

    /** 工具列是否超出可見寬度。 */
    private fun hasToolbarOverflow(): Boolean = toolbarContentWidth() > toolbarViewportWidth()

    /** 游標是否位於工具列按鈕 viewport。 */
    private fun isInsideToolbar(mouseX: Double, mouseY: Double): Boolean = mouseX >= TOOLBAR_MARGIN &&
        mouseX < toolbarViewportRight() &&
        mouseY >= TOOLBAR_TOP &&
        mouseY < TOOLBAR_BOTTOM

    /** 游標是否位於包含 scrollbar 的工具列區域。 */
    private fun isInsideToolbarArea(mouseX: Double, mouseY: Double): Boolean = mouseX >= TOOLBAR_MARGIN &&
        mouseX < toolbarViewportRight() &&
        mouseY >= TOOLBAR_TOP &&
        mouseY < TOOLBAR_SCROLLBAR_TOP + TOOLBAR_SCROLLBAR_HEIGHT

    /** 游標是否位於工具列 scrollbar。 */
    private fun isOverToolbarScrollbar(mouseX: Double, mouseY: Double): Boolean = mouseX >= TOOLBAR_MARGIN &&
        mouseX < toolbarViewportRight() &&
        mouseY >= TOOLBAR_SCROLLBAR_TOP &&
        mouseY < TOOLBAR_SCROLLBAR_TOP + TOOLBAR_SCROLLBAR_HEIGHT

    /** 依可見比例與捲動量計算工具列 scrollbar thumb。 */
    private fun toolbarScrollbarThumb(): ToolbarScrollbarThumb {
        val viewportWidth = toolbarViewportWidth()
        val thumbWidth = (viewportWidth.toDouble() * viewportWidth / toolbarContentWidth())
            .roundToInt().coerceIn(MIN_TOOLBAR_THUMB_WIDTH, viewportWidth)
        val travel = viewportWidth - thumbWidth
        val left = TOOLBAR_MARGIN + if (maximumToolbarScroll() == 0.0) 0 else (toolbarScroll / maximumToolbarScroll() * travel).roundToInt()
        return ToolbarScrollbarThumb(left, left + thumbWidth)
    }

    /** 將 thumb 左界轉換為工具列捲動量。 */
    private fun toolbarScrollbarFromThumb(thumbLeft: Double): Double {
        val thumb = toolbarScrollbarThumb()
        val travel = (toolbarViewportWidth() - thumb.width).coerceAtLeast(1)
        val relative = (thumbLeft - TOOLBAR_MARGIN).coerceIn(0.0, travel.toDouble())
        return relative / travel * maximumToolbarScroll()
    }

    /** 只有未手動隱藏且未拖曳時顯示 editor 控制項。 */
    private fun controlsVisible(): Boolean = !controlsManuallyHidden && dragging == null

    /** 將比例轉為整數百分比。 */
    private fun percent(value: Double): Int = (value * 100).roundToInt()

    /** 靠近畫面中線時吸附至百分之五十。 */
    private fun snap(value: Double): Double = if (kotlin.math.abs(value - 0.5) <= SNAP_THRESHOLD) 0.5 else value

    /** 取得一個預覽框目前的完整 bounds。 */
    private fun bounds(element: HudElement): MahjongHudBounds {
        val (elementWidth, elementHeight) = previewSize(element)
        val x = when (element) {
            HudElement.COMPACT -> hudCoordinate(draft.compactPromptX, width, elementWidth)
            else -> (width - elementWidth) / 2
        }
        val yRatio = when (element) {
            HudElement.DECISION -> draft.decisionPanelY
            HudElement.COMPACT -> draft.compactPromptY
            HudElement.ANALYSIS -> draft.discardAnalysisY
        }
        return MahjongHudBounds(x, hudCoordinate(yRatio, height, elementHeight), elementWidth, elementHeight)
    }

    /** 依螢幕大小限制預覽框尺寸。 */
    private fun previewSize(element: HudElement): Pair<Int, Int> = when (element) {
        HudElement.DECISION -> minOf(scenario.width, width - 16).coerceAtLeast(1) to
            minOf(scenario.height, height - 16).coerceAtLeast(1)
        HudElement.COMPACT -> minOf(190, width - 16).coerceAtLeast(1) to minOf(46, height - 16).coerceAtLeast(1)
        HudElement.ANALYSIS -> minOf(220, width - 16).coerceAtLeast(1) to minOf(72, height - 16).coerceAtLeast(1)
    }

    /** 繪製與正式 HUD 視覺語言一致的簡化拖曳預覽。 */
    private fun renderPreview(
        context: DrawContext,
        element: HudElement,
        bounds: MahjongHudBounds,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = bounds.contains(mouseX.toDouble(), mouseY.toDouble())
        val background = if (hovered) PREVIEW_HOVER_COLOR else PREVIEW_COLOR
        context.fill(bounds.left, bounds.top, bounds.right, bounds.bottom, background)
        context.drawBorder(bounds.left, bounds.top, bounds.width, bounds.height, SELECTED_BORDER_COLOR)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable(element.translationKey),
            bounds.left + bounds.width / 2,
            bounds.top + 7,
            if (hovered) TITLE_COLOR else 0xFFFFFF,
        )
        val position = when (element) {
            HudElement.DECISION -> "Y ${percent(draft.decisionPanelY)}%"
            HudElement.COMPACT -> "X ${percent(draft.compactPromptX)}%  Y ${percent(draft.compactPromptY)}%"
            HudElement.ANALYSIS -> "Y ${percent(draft.discardAnalysisY)}%"
        }
        context.drawCenteredTextWithShadow(textRenderer, position, bounds.left + bounds.width / 2, bounds.top + 22, 0xB0B0B0)
    }

    /** 非作用中 HUD 以淡暗背景、外框與置中名稱表示，不使用額外名稱色塊。 */
    private fun renderOutline(context: DrawContext, element: HudElement, bounds: MahjongHudBounds) {
        context.fill(bounds.left, bounds.top, bounds.right, bounds.bottom, OUTLINE_BACKGROUND_COLOR)
        context.drawBorder(bounds.left, bounds.top, bounds.width, bounds.height, OUTLINE_COLOR)
        val label = Text.translatable(element.translationKey)
        context.drawCenteredTextWithShadow(textRenderer, label, bounds.left + bounds.width / 2, bounds.top + 7, OUTLINE_TEXT_COLOR)
    }

    /** 非作用中 HUD 的兩種可見性。 */
    private enum class PreviewVisibility(val translationKey: String) {
        /** 只顯示外框與名稱。 */
        OUTLINE(MinecraftClientConfigScreenKeys.HUD_LAYOUT_VISIBILITY_OUTLINE),

        /** 完全隱藏。 */
        HIDDEN(MinecraftClientConfigScreenKeys.HUD_LAYOUT_VISIBILITY_HIDDEN),
    }

    /** 操作面板可切換的代表性動態尺寸情境。 */
    private enum class PreviewScenario(
        /** 情境按鈕翻譯鍵。 */
        val translationKey: String,
        /** 預覽寬度。 */
        val width: Int,
        /** 預覽高度。 */
        val height: Int,
    ) {
        /** 一般鳴牌。 */
        CALL(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SCENARIO_CALL, 300, 104),

        /** 立直宣告。 */
        RIICHI(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SCENARIO_RIICHI, 380, 112),

        /** 九種九牌等長牌列操作。 */
        ABORTIVE_DRAW(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SCENARIO_ABORTIVE_DRAW, 440, 124),
    }

    /** 頂部工具列的三種下拉選單。 */
    private enum class DropdownKind(val translationKey: String) {
        /** 目前編輯的 HUD。 */
        HUD(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_HUD),

        /** 目前 HUD 的預覽方式。 */
        VISIBILITY(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_VISIBILITY),

        /** 操作面板的代表性內容情境。 */
        SCENARIO(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_SCENARIO),
    }

    /** 下拉選單的一個宣告式選項。 */
    private data class DropdownOption(
        /** 顯示文字。 */
        val label: Text,
        /** 是否為目前選項。 */
        val selected: Boolean,
        /** 選取後執行的狀態更新。 */
        val select: () -> Unit,
    )

    /** 一個工具列按鈕及其不受捲動影響的內容座標。 */
    private data class ToolbarEntry(
        /** 原版按鈕。 */
        val button: ButtonWidget,
        /** 工具列內容座標。 */
        val contentX: Int,
        /** 此按鈕控制的下拉選單。 */
        val kind: DropdownKind,
    )

    /** 工具列 scrollbar thumb 邊界。 */
    private data class ToolbarScrollbarThumb(
        /** 左邊界。 */
        val left: Int,
        /** 右邊界。 */
        val right: Int,
    ) {
        /** 寬度。 */
        val width: Int
            get() = right - left
    }

    /** 可拖曳的三個 HUD 配置區塊。 */
    private enum class HudElement(val translationKey: String) {
        /** 操作面板。 */
        DECISION(MinecraftClientConfigScreenKeys.HUD_LAYOUT_DECISION_PANEL),

        /** 一般倒數與等待提醒。 */
        COMPACT(MinecraftClientConfigScreenKeys.HUD_LAYOUT_COMPACT_PROMPT),

        /** 打牌分析。 */
        ANALYSIS(MinecraftClientConfigScreenKeys.HUD_LAYOUT_DISCARD_ANALYSIS),
    }

    /** Editor 配色與吸附常數。 */
    private companion object {
        /** 中線吸附比例範圍。 */
        const val SNAP_THRESHOLD = 0.015

        /** 工具列左右邊距。 */
        const val TOOLBAR_MARGIN = 12

        /** 工具列按鈕上界。 */
        const val TOOLBAR_TOP = 26

        /** 工具列按鈕下界。 */
        const val TOOLBAR_BOTTOM = TOOLBAR_TOP + 20

        /** 工具列 scrollbar 上界。 */
        const val TOOLBAR_SCROLLBAR_TOP = TOOLBAR_BOTTOM + 3

        /** 工具列 scrollbar 高度。 */
        const val TOOLBAR_SCROLLBAR_HEIGHT = 4

        /** 工具列按鈕間距。 */
        const val TOOLBAR_GAP = 4

        /** 每個下拉選單按鈕寬度。 */
        const val DROPDOWN_WIDTH = 132

        /** 固定隱藏控制項按鈕寬度。 */
        const val HIDE_CONTROLS_WIDTH = 104

        /** 固定其他 HUD 預覽按鈕寬度。 */
        const val OTHER_PREVIEW_WIDTH = 156

        /** 下拉 popup 寬度。 */
        const val DROPDOWN_POPUP_WIDTH = 156

        /** 下拉 popup 每列高度。 */
        const val DROPDOWN_OPTION_HEIGHT = 20

        /** 下拉 popup 上界。 */
        const val TOOLBAR_POPUP_TOP = TOOLBAR_SCROLLBAR_TOP + TOOLBAR_SCROLLBAR_HEIGHT + 4

        /** 工具列 thumb 最小寬度。 */
        const val MIN_TOOLBAR_THUMB_WIDTH = 18

        /** 滾輪每格移動距離。 */
        const val TOOLBAR_SCROLL_STEP = 48.0

        /** 工具列 scrollbar 軌道色。 */
        const val TOOLBAR_TRACK_COLOR = 0xFF26333D.toInt()

        /** 工具列 scrollbar thumb 色。 */
        const val TOOLBAR_THUMB_COLOR = 0xFF8796A3.toInt()

        /** 全畫面半透明遮罩。 */
        const val SCREEN_OVERLAY_COLOR = 0x88000000.toInt()

        /** HUD 預覽背景。 */
        const val PREVIEW_COLOR = 0xCC101820.toInt()

        /** HUD 預覽 hover 背景。 */
        const val PREVIEW_HOVER_COLOR = 0xDD36566B.toInt()

        /** 僅外框預覽仍保留的極淡背景。 */
        const val OUTLINE_BACKGROUND_COLOR = 0x30202B35

        /** 目前選取 HUD 的強調外框色。 */
        const val SELECTED_BORDER_COLOR = 0xFFE2B84B.toInt()

        /** 非作用中 HUD 外框色。 */
        const val OUTLINE_COLOR = 0x668796A3

        /** 非作用中 HUD 名稱的低對比文字色。 */
        const val OUTLINE_TEXT_COLOR = 0x668796A3

        /** 下拉選單外框色。 */
        const val DROPDOWN_BORDER_COLOR = 0xFF8796A3.toInt()

        /** 下拉選單背景色。 */
        const val DROPDOWN_BACKGROUND_COLOR = 0xF018222B.toInt()

        /** 下拉選單 hover 背景色。 */
        const val DROPDOWN_HOVER_COLOR = 0xF0364B5C.toInt()

        /** 僅位於每顆 editor 按鈕自身範圍內的不透明底色。 */
        const val EDITOR_BUTTON_BACKING_COLOR = 0xFF101820.toInt()

        /** Editor 控制項高於不同 GUI render layer 中 HUD 文字的 Z 位移。 */
        const val EDITOR_CONTROLS_Z = 400.0

        /** 下拉選單高於其他 editor 控制項的額外 Z 位移。 */
        const val DROPDOWN_Z_OFFSET = 100.0

        /** 中心參考線。 */
        const val GUIDE_COLOR = 0x446A7C8C

        /** 標題與 hover 強調色。 */
        const val TITLE_COLOR = 0xFFD54F
    }
}

/** HUD editor 返回時使用的三選項未保存變更畫面。 */
private class HudLayoutUnsavedChangesScreen(
    private val editor: MahjongHudLayoutEditorScreen,
    private val apply: (Boolean) -> Unit,
    private val discard: () -> Unit,
) : Screen(Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_UNSAVED_TITLE)) {
    /** 建立套用、放棄與繼續編輯三個按鈕。 */
    override fun init() {
        val buttonWidth = minOf(160, width - 24)
        val left = (width - buttonWidth) / 2
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.APPLY_AND_BACK)) { apply(true) }
                .dimensions(left, height / 2, buttonWidth, 20).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.DISCARD_CHANGES)) { discard() }
                .dimensions(left, height / 2 + 24, buttonWidth, 20).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.CONTINUE_EDITING)) { client?.setScreen(editor) }
                .dimensions(left, height / 2 + 48, buttonWidth, 20).build(),
        )
    }

    /** 確認畫面不暫停遊戲。 */
    override fun shouldPause(): Boolean = false

    /** Esc 返回 editor，避免無聲放棄變更。 */
    override fun close() {
        client?.setScreen(editor)
    }

    /** 繪製確認標題與說明。 */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xAA000000.toInt())
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 42, 0xFFD54F)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_UNSAVED_MESSAGE),
            width / 2,
            height / 2 - 26,
            0xFFFFFF,
        )
        super.render(context, mouseX, mouseY, delta)
    }
}
