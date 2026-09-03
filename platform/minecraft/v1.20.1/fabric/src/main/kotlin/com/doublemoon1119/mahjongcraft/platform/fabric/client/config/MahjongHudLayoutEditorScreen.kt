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

/**
 * 原生 HUD 位置編輯器；拖曳只修改草稿，套用時才交由父設定畫面原子保存。
 *
 * 這個畫面只負責原版 widget 生命週期與繪製：全部狀態轉換（拖曳、選取、草稿比較）委派給
 * [MahjongHudLayoutEditorModel]，工具列幾何與點擊區域判定委派給 [MahjongHudToolbarLayout]，
 * 兩者都不依賴 Minecraft 型別，因此可以直接以 JVM 測試驗證。
 */
class MahjongHudLayoutEditorScreen(
    private val parent: MahjongClientConfigScreen,
    initialLayout: MahjongHudLayoutConfig,
) : Screen(Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_TITLE)) {
    /** 編輯器的全部可測試狀態；拖曳、選取、草稿與控制項顯示都由它決定。 */
    private var model = MahjongHudLayoutEditorModel(baseline = initialLayout)

    /** 最近一次保存是否失敗。 */
    private var saveFailed = false

    /** 頂部工具列按鈕及其未捲動內容座標。 */
    private val toolbarEntries = mutableListOf<ToolbarEntry>()

    /** 固定在工具列右側、不參與水平捲動的控制項隱藏按鈕。 */
    private var hideControlsButton: ButtonWidget? = null

    /** 固定在隱藏按鈕左側的其他 HUD 預覽選單按鈕。 */
    private var otherHudPreviewButton: ButtonWidget? = null

    /** 目前展開的下拉選單。 */
    private var openDropdown: HudDropdownKind? = null

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
            RestartableMarqueeButtonWidget.builder(dropdownButtonText(HudDropdownKind.VISIBILITY)) {
                openDropdown = if (openDropdown == HudDropdownKind.VISIBILITY) null else HudDropdownKind.VISIBILITY
            }.dimensions(
                width - MahjongHudToolbarLayout.MARGIN - MahjongHudToolbarLayout.HIDE_CONTROLS_WIDTH -
                    MahjongHudToolbarLayout.GAP - MahjongHudToolbarLayout.OTHER_PREVIEW_WIDTH,
                MahjongHudToolbarLayout.TOP,
                MahjongHudToolbarLayout.OTHER_PREVIEW_WIDTH,
                MahjongHudToolbarLayout.BUTTON_HEIGHT,
            ).build(),
        )
        hideControlsButton = addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_HIDE_CONTROLS)) {
                model = model.withControlsHidden(true)
                openDropdown = null
                updateControlVisibility()
            }.dimensions(
                width - MahjongHudToolbarLayout.MARGIN - MahjongHudToolbarLayout.HIDE_CONTROLS_WIDTH,
                MahjongHudToolbarLayout.TOP,
                MahjongHudToolbarLayout.HIDE_CONTROLS_WIDTH,
                MahjongHudToolbarLayout.BUTTON_HEIGHT,
            ).build(),
        )
        resetButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_RESET)) {
                model = model.reset()
                saveFailed = false
                refreshButtons()
            }.dimensions(footer.resetX, y, footer.resetWidth, 20).build(),
        )
        undoButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.UNDO)) {
                model = model.undo()
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
        toolbarScroll = toolbarScroll.coerceIn(0.0, toolbarLayout().maximumScroll)
        updateToolbarPositions()
        updateControlVisibility()
    }

    /** 建立 HUD、預覽模式與操作情境三個下拉選單按鈕。 */
    private fun addDropdownButtons() {
        HudDropdownKind.entries.forEach { kind ->
            if (kind == HudDropdownKind.VISIBILITY) return@forEach
            if (kind == HudDropdownKind.SCENARIO && model.selectedElement != HudElement.DECISION) return@forEach
            addToolbarButton(
                RestartableMarqueeButtonWidget.builder(dropdownButtonText(kind)) {
                    openDropdown = if (openDropdown == kind) null else kind
                }.dimensions(
                    0,
                    MahjongHudToolbarLayout.TOP,
                    MahjongHudToolbarLayout.DROPDOWN_WIDTH,
                    MahjongHudToolbarLayout.BUTTON_HEIGHT,
                ).build(),
                kind,
            )
        }
    }

    /** Esc 使用與返回按鈕相同的未保存變更保護。 */
    override fun close() {
        if (model.controlsManuallyHidden) {
            model = model.withControlsHidden(false)
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
            if (element == model.selectedElement) {
                renderPreview(context, element, bounds(element), mouseX, mouseY)
            } else {
                when (model.otherHudVisibility) {
                    HudPreviewVisibility.OUTLINE -> renderOutline(context, element, bounds(element))
                    HudPreviewVisibility.HIDDEN -> Unit
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
        if (model.controlsVisible) {
            context.matrices.push()
            context.matrices.translate(0.0, 0.0, EDITOR_CONTROLS_Z)
            renderFixedControls(context, mouseX, mouseY, delta)
            renderToolbar(context, mouseX, mouseY, delta)
            context.matrices.pop()
        } else if (model.controlsManuallyHidden && model.dragging == null) {
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
        val toolbar = toolbarLayout()
        if (model.controlsVisible && button == 0 && toolbar.hasOverflow && toolbar.isOverScrollbar(mouseX, mouseY)) {
            val thumb = toolbar.thumb(toolbarScroll)
            if (mouseX !in thumb.left.toDouble()..thumb.right.toDouble()) {
                toolbarScroll = toolbar.scrollFromThumb(mouseX - thumb.width / 2.0)
                updateToolbarPositions()
            }
            draggingToolbarScrollbar = true
            toolbarDragStartX = mouseX
            toolbarDragStartScroll = toolbarScroll
            return true
        }
        if (model.controlsVisible && button == 0 && handleDropdownClick(mouseX, mouseY)) return true
        if (
            model.controlsVisible &&
            listOfNotNull(otherHudPreviewButton, hideControlsButton).any { it.mouseClicked(mouseX, mouseY, button) }
        ) {
            return true
        }
        if (model.controlsVisible && toolbar.isInsideButtons(mouseX, mouseY)) {
            val handled = toolbarEntries.any { it.button.visible && it.button.mouseClicked(mouseX, mouseY, button) }
            if (!handled) openDropdown = null
            return handled
        }
        openDropdown = null
        if (
            model.controlsVisible &&
            mouseY >= MahjongHudToolbarLayout.TOP &&
            mouseY < MahjongHudToolbarLayout.BOTTOM
        ) {
            return false
        }
        if (model.controlsVisible && super.mouseClicked(mouseX, mouseY, button)) return true
        if (button == 0) {
            val element = model.hitTest(
                mouseX = mouseX,
                mouseY = mouseY,
                screenWidth = width,
                screenHeight = height,
            )
            if (element != null) {
                model = model.beginDrag(
                    element = element,
                    mouseX = mouseX,
                    mouseY = mouseY,
                    screenWidth = width,
                    screenHeight = height,
                )
                updateControlVisibility()
                return true
            }
        }
        return false
    }

    /** 依 HUD 可調整軸更新比例，並由比例座標自然限制完整 bounds 在畫面內。 */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (draggingToolbarScrollbar && button == 0) {
            toolbarScroll = toolbarLayout().scrollFromDrag(
                startScroll = toolbarDragStartScroll,
                pointerDelta = mouseX - toolbarDragStartX,
            )
            updateToolbarPositions()
            return true
        }
        if (model.dragging == null) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        if (button != 0) return false
        model = model.dragTo(
            mouseX = mouseX,
            mouseY = mouseY,
            screenWidth = width,
            screenHeight = height,
        )
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
        if (button == 0 && model.dragging != null) {
            model = model.endDrag()
            clearAndInit()
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /** 工具列範圍內的滾輪輸入轉為水平捲動。 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val toolbar = toolbarLayout()
        if (model.controlsVisible && toolbar.hasOverflow && toolbar.isInsideArea(mouseX, mouseY)) {
            toolbarScroll = toolbar.scrollFromWheel(currentScroll = toolbarScroll, amount = amount)
            updateToolbarPositions()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    /** 透過父畫面的完整草稿保存流程套用 HUD 配置。 */
    private fun apply(returnAfterSave: Boolean) {
        if (!model.hasUnsavedChanges) {
            if (returnAfterSave) client?.setScreen(parent)
            return
        }
        if (parent.applyHudLayout(model.draft)) {
            model = model.markApplied()
            saveFailed = false
            refreshButtons()
            if (returnAfterSave) client?.setScreen(parent)
        } else {
            saveFailed = true
        }
    }

    /** 無變更時直接返回；有變更時開啟三選項確認畫面。 */
    private fun requestReturn() {
        if (!model.hasUnsavedChanges) {
            client?.setScreen(parent)
        } else {
            client?.setScreen(HudLayoutUnsavedChangesScreen(this, ::apply, { client?.setScreen(parent) }))
        }
    }

    /** 依目前草稿更新按鈕狀態與差異 tooltip。 */
    private fun refreshButtons() {
        val tooltip = if (!model.hasUnsavedChanges) {
            null
        } else {
            Tooltip.of(
                clientConfigDifferenceText(
                    MahjongClientConfigState(hudLayout = model.baseline),
                    MahjongClientConfigState(hudLayout = model.draft),
                ),
            )
        }
        applyButton?.active = model.hasUnsavedChanges
        undoButton?.active = model.hasUnsavedChanges
        resetButton?.active = !model.isDefault
        applyButton?.tooltip = tooltip
        undoButton?.tooltip = tooltip
    }

    /** 依手動隱藏、拖曳狀態與目前焦點同步全部原版 widget 的顯示狀態。 */
    private fun updateControlVisibility() {
        val visible = model.controlsVisible
        children().filterIsInstance<ButtonWidget>().forEach { it.visible = visible }
    }

    /** 將按鈕加入水平工具列並配置下一個內容座標。 */
    private fun <T : ButtonWidget> addToolbarButton(button: T, kind: HudDropdownKind): T {
        val contentX = toolbarEntries.lastOrNull()?.let {
            it.contentX + it.button.width + MahjongHudToolbarLayout.GAP
        } ?: 0
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
        val toolbar = toolbarLayout()
        context.enableScissor(
            MahjongHudToolbarLayout.MARGIN,
            MahjongHudToolbarLayout.TOP,
            toolbar.viewportRight,
            MahjongHudToolbarLayout.BOTTOM,
        )
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
        if (toolbar.hasOverflow) {
            context.fill(
                MahjongHudToolbarLayout.MARGIN,
                MahjongHudToolbarLayout.SCROLLBAR_TOP,
                toolbar.viewportRight,
                MahjongHudToolbarLayout.SCROLLBAR_TOP + MahjongHudToolbarLayout.SCROLLBAR_HEIGHT,
                TOOLBAR_TRACK_COLOR,
            )
            val thumb = toolbar.thumb(toolbarScroll)
            context.fill(
                thumb.left,
                MahjongHudToolbarLayout.SCROLLBAR_TOP,
                thumb.right,
                MahjongHudToolbarLayout.SCROLLBAR_TOP + MahjongHudToolbarLayout.SCROLLBAR_HEIGHT,
                TOOLBAR_THUMB_COLOR,
            )
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
        val left = toolbarLayout().dropdownLeft(anchor.x)
        val top = MahjongHudToolbarLayout.POPUP_TOP
        val bottom = top + options.size * MahjongHudToolbarLayout.DROPDOWN_OPTION_HEIGHT
        context.fill(
            left - 1,
            top - 1,
            left + MahjongHudToolbarLayout.DROPDOWN_POPUP_WIDTH + 1,
            bottom + 1,
            DROPDOWN_BORDER_COLOR,
        )
        options.forEachIndexed { index, option ->
            val optionTop = top + index * MahjongHudToolbarLayout.DROPDOWN_OPTION_HEIGHT
            val hovered = mouseX in left until left + MahjongHudToolbarLayout.DROPDOWN_POPUP_WIDTH &&
                mouseY in optionTop until optionTop + MahjongHudToolbarLayout.DROPDOWN_OPTION_HEIGHT
            context.fill(
                left,
                optionTop,
                left + MahjongHudToolbarLayout.DROPDOWN_POPUP_WIDTH,
                optionTop + MahjongHudToolbarLayout.DROPDOWN_OPTION_HEIGHT,
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
        val index = toolbarLayout().dropdownOptionIndexAt(
            mouseX = mouseX,
            mouseY = mouseY,
            anchorX = anchor.x,
            optionCount = options.size,
        ) ?: return false
        options[index].select()
        openDropdown = null
        clearAndInit()
        return true
    }

    /** 取得指定下拉選單目前可選項目與穩定順序。 */
    private fun dropdownOptions(kind: HudDropdownKind): List<DropdownOption> = when (kind) {
        HudDropdownKind.HUD -> HudElement.entries.map { element ->
            DropdownOption(Text.translatable(element.translationKey), element == model.selectedElement) {
                model = model.selectElement(element)
            }
        }
        HudDropdownKind.VISIBILITY -> HudPreviewVisibility.entries.map { visibility ->
            DropdownOption(Text.translatable(visibility.translationKey), model.otherHudVisibility == visibility) {
                model = model.selectVisibility(visibility)
            }
        }
        HudDropdownKind.SCENARIO -> HudPreviewScenario.entries.map { preview ->
            DropdownOption(Text.translatable(preview.translationKey), model.scenario == preview) {
                model = model.selectScenario(preview)
            }
        }
    }

    /** 組合下拉選單欄位名稱、目前值與展開符號。 */
    private fun dropdownButtonText(kind: HudDropdownKind): Text = Text.translatable(
        kind.translationKey,
        when (kind) {
            HudDropdownKind.HUD -> Text.translatable(model.selectedElement.translationKey)
            HudDropdownKind.VISIBILITY -> Text.translatable(model.otherHudVisibility.translationKey)
            HudDropdownKind.SCENARIO -> Text.translatable(model.scenario.translationKey)
        },
    )

    /** 取得下拉選單按鈕，不依工具列內容順序推斷控制對象。 */
    private fun dropdownAnchor(kind: HudDropdownKind): ButtonWidget? = when (kind) {
        HudDropdownKind.VISIBILITY -> otherHudPreviewButton
        else -> toolbarEntries.firstOrNull { it.kind == kind }?.button
    }

    /** 依目前捲動量更新工具列按鈕實際畫面位置。 */
    private fun updateToolbarPositions() {
        val contentOffset = toolbarLayout().contentOffset(toolbarScroll)
        toolbarEntries.forEach { entry -> entry.button.x = contentOffset + entry.contentX }
    }

    /** 依目前畫面寬度與工具列內容寬度建立幾何計算。 */
    private fun toolbarLayout(): MahjongHudToolbarLayout = MahjongHudToolbarLayout(
        screenWidth = width,
        contentWidth = toolbarContentWidth(),
    )

    /** 工具列全部按鈕所需內容寬度。 */
    private fun toolbarContentWidth(): Int = toolbarEntries.lastOrNull()?.let { it.contentX + it.button.width } ?: 0

    /** 將比例轉為整數百分比。 */
    private fun percent(value: Double): Int = (value * 100).roundToInt()

    /** 取得一個預覽框目前的完整 bounds。 */
    private fun bounds(element: HudElement): MahjongHudBounds = model.bounds(
        element = element,
        screenWidth = width,
        screenHeight = height,
    )

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
            HudElement.DECISION -> "Y ${percent(model.draft.decisionPanelY)}%"
            HudElement.COMPACT -> "X ${percent(model.draft.compactPromptX)}%  Y ${percent(model.draft.compactPromptY)}%"
            HudElement.ANALYSIS -> "Y ${percent(model.draft.discardAnalysisY)}%"
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
        val kind: HudDropdownKind,
    )

    /** Editor 配色與 Z 位移常數；工具列與預覽的幾何常數見 [MahjongHudToolbarLayout]。 */
    private companion object {
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
