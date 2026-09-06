package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.SettingsFooterLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.UnsavedChangesConfirmationScreen
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
 * [MahjongHudLayoutEditorModel]，第二行二級選項的水平捲動幾何計算委派給 [MahjongHudToolbarLayout]，
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

    /** 套用按鈕。 */
    private var applyButton: ButtonWidget? = null

    /** 復原按鈕。 */
    private var undoButton: ButtonWidget? = null

    /** 重設配置按鈕。 */
    private var resetButton: ButtonWidget? = null

    /**
     * 第二行二級選項按鈕與其不受捲動影響的內容座標；目前只有操作面板的情境選擇會用到，但保留清單
     * 結構讓未來規則模組能安全地登記更多二級選項。
     */
    private val secondaryRowEntries = mutableListOf<Pair<ButtonWidget, Int>>()

    /** 第二行目前的水平捲動量。 */
    private var secondaryRowScroll = 0.0

    /** 是否正在拖曳第二行的 scrollbar。 */
    private var draggingSecondaryRowScrollbar = false

    /** 拖曳第二行 scrollbar 起點的游標 X 與捲動量。 */
    private var secondaryRowDragStartX = 0.0
    private var secondaryRowDragStartScroll = 0.0

    /**
     * 按鈕各自的 tooltip 內容（每個元素一行），於 [renderControls] 結尾統一補畫在最上層；不透過原版
     * [ButtonWidget.tooltip]，避免第二行按鈕的背景／文字在同一輪繪製中畫到第一行按鈕彈出的 tooltip
     * 上面。用多行清單而不是單一 Text 內嵌 `\n`，因為 [DrawContext.drawTooltip] 的單行版本不會把
     * `\n` 拆成新的一行，只會把它當成一個缺字字元畫出來。
     */
    private val toolbarTooltips = mutableMapOf<ButtonWidget, List<Text>>()

    /** Editor 不暫停單人遊戲或 integrated server。 */
    override fun shouldPause(): Boolean = false

    /** 建立工具列按鈕與固定單列的重設、復原、套用與返回按鈕。 */
    override fun init() {
        val footer = SettingsFooterLayout.create(12, width - 24, 104, 6)
        val y = height - 28
        addToolbarButtons()
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
        updateControlVisibility()
    }

    /**
     * 建立固定第一行的 HUD、其他 HUD 預覽、隱藏控制項三顆全域按鈕，三等分整個可用寬度填滿整行；
     * 以及目前 HUD 元件有二級選項時才出現的第二行（例如操作面板的情境選擇）。
     */
    private fun addToolbarButtons() {
        toolbarTooltips.clear()
        val available = width - MahjongHudToolbarLayout.MARGIN * 2
        val buttonWidth = (available - MahjongHudToolbarLayout.GAP * 2) / 3
        var x = MahjongHudToolbarLayout.MARGIN
        val hud = hudSelectorButton(buttonWidth)
        hud.x = x
        hud.y = MahjongHudToolbarLayout.TOP
        addDrawableChild(hud)
        x += hud.width + MahjongHudToolbarLayout.GAP
        val visibility = visibilitySelectorButton(buttonWidth)
        visibility.x = x
        visibility.y = MahjongHudToolbarLayout.TOP
        addDrawableChild(visibility)
        x += visibility.width + MahjongHudToolbarLayout.GAP
        // 最後一顆吃下三等分無法整除的餘數，確保整行剛好填滿到右邊界。
        val hideControls = hideControlsButtonWidget(width - MahjongHudToolbarLayout.MARGIN - x)
        hideControls.x = x
        hideControls.y = MahjongHudToolbarLayout.TOP
        addDrawableChild(hideControls)
        addSecondaryRowButtons()
    }

    /** 建立第二行二級選項按鈕；目前只有編輯操作面板時的情境選擇。 */
    private fun addSecondaryRowButtons() {
        secondaryRowEntries.clear()
        val buttons = buildList {
            if (model.selectedElement == HudElement.DECISION) add(scenarioSelectorButton())
        }
        var contentX = 0
        buttons.forEach { button ->
            secondaryRowEntries += button to contentX
            contentX += button.width + MahjongHudToolbarLayout.GAP
            addDrawableChild(button)
        }
        secondaryRowScroll = secondaryRowScroll.coerceIn(0.0, secondaryRowLayout().maximumScroll)
        updateSecondaryRowPositions()
    }

    /** 循環切換目前編輯的 HUD；tooltip 條列所有 HUD 補足失去的一覽性。 */
    private fun hudSelectorButton(width: Int): ButtonWidget {
        val button = RestartableMarqueeButtonWidget.builder(
            Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_HUD, Text.translatable(model.selectedElement.translationKey)),
        ) {
            val entries = HudElement.entries
            model = model.selectElement(entries[(entries.indexOf(model.selectedElement) + 1) % entries.size])
            clearAndInit()
        }.dimensions(0, 0, width, MahjongHudToolbarLayout.BUTTON_HEIGHT).build()
        toolbarTooltips[button] = selectionTooltip(HudElement.entries.map { Text.translatable(it.translationKey) to (it == model.selectedElement) })
        return button
    }

    /** 循環切換操作面板的代表性內容情境；只在編輯操作面板時顯示。 */
    private fun scenarioSelectorButton(): ButtonWidget {
        val button = RestartableMarqueeButtonWidget.builder(
            Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_SCENARIO, Text.translatable(model.scenario.translationKey)),
        ) {
            val entries = HudPreviewScenario.entries
            model = model.selectScenario(entries[(entries.indexOf(model.scenario) + 1) % entries.size])
            clearAndInit()
        }.dimensions(0, 0, MahjongHudToolbarLayout.SELECTOR_WIDTH, MahjongHudToolbarLayout.BUTTON_HEIGHT).build()
        toolbarTooltips[button] = selectionTooltip(HudPreviewScenario.entries.map { Text.translatable(it.translationKey) to (it == model.scenario) })
        return button
    }

    /** 循環切換非作用中 HUD 的預覽方式；與目前編輯哪個 HUD 無關，屬於全域設定。 */
    private fun visibilitySelectorButton(width: Int): ButtonWidget {
        val button = RestartableMarqueeButtonWidget.builder(
            Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_VISIBILITY, Text.translatable(model.otherHudVisibility.translationKey)),
        ) {
            val entries = HudPreviewVisibility.entries
            model = model.selectVisibility(entries[(entries.indexOf(model.otherHudVisibility) + 1) % entries.size])
            clearAndInit()
        }.dimensions(0, 0, width, MahjongHudToolbarLayout.BUTTON_HEIGHT).build()
        toolbarTooltips[button] = selectionTooltip(HudPreviewVisibility.entries.map { Text.translatable(it.translationKey) to (it == model.otherHudVisibility) })
        return button
    }

    /** 全域的隱藏控制項按鈕；與目前編輯哪個 HUD 無關。 */
    private fun hideControlsButtonWidget(width: Int): ButtonWidget = ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_HIDE_CONTROLS)) {
        model = model.withControlsHidden(true)
        updateControlVisibility()
    }.dimensions(0, 0, width, MahjongHudToolbarLayout.BUTTON_HEIGHT).build()

    /** 建立「目前值＋可用選項」清單 tooltip（每行一個元素），補足循環切換按鈕無法一次看到所有選項的缺點。 */
    private fun selectionTooltip(options: List<Pair<Text, Boolean>>): List<Text> = buildList {
        val current = options.first { it.second }.first
        add(Text.translatable(MinecraftClientConfigScreenKeys.CURRENT_VALUE, current).formatted(Formatting.GREEN))
        add(Text.translatable(MinecraftClientConfigScreenKeys.AVAILABLE_OPTIONS).formatted(Formatting.GOLD))
        options.forEach { (label, selected) ->
            add(Text.literal("• ").append(label.copy().formatted(if (selected) Formatting.GREEN else Formatting.WHITE)))
        }
    }

    /** 第二行內容總寬度，供水平捲動幾何使用。 */
    private fun secondaryRowContentWidth(): Int = secondaryRowEntries.lastOrNull()?.let { (button, contentX) -> contentX + button.width } ?: 0

    private fun secondaryRowLayout(): MahjongHudToolbarLayout = MahjongHudToolbarLayout(width, secondaryRowContentWidth())

    /** 第二行按鈕上界；緊接在第一行下方。 */
    private fun secondaryRowTop(): Int = MahjongHudToolbarLayout.TOP + MahjongHudToolbarLayout.BUTTON_HEIGHT + MahjongHudToolbarLayout.ROW_GAP

    private fun secondaryRowScrollbarTop(): Int = secondaryRowTop() + MahjongHudToolbarLayout.BUTTON_HEIGHT + MahjongHudToolbarLayout.ROW_GAP

    /** 依目前捲動量更新第二行按鈕的實際畫面座標。 */
    private fun updateSecondaryRowPositions() {
        val offset = secondaryRowLayout().contentOffset(secondaryRowScroll)
        secondaryRowEntries.forEach { (button, contentX) ->
            button.x = offset + contentX
            button.y = secondaryRowTop()
        }
    }

    /** 游標是否位於第二行按鈕列或其下方 scrollbar 的整體區域，決定滾輪是否轉為水平捲動。 */
    private fun isInsideSecondaryRowArea(mouseX: Double, mouseY: Double): Boolean {
        val layout = secondaryRowLayout()
        return mouseX >= MahjongHudToolbarLayout.MARGIN &&
            mouseX < MahjongHudToolbarLayout.MARGIN + layout.viewportWidth &&
            mouseY >= secondaryRowTop() &&
            mouseY < secondaryRowScrollbarTop() + MahjongHudToolbarLayout.SCROLLBAR_HEIGHT
    }

    private fun isOverSecondaryRowScrollbar(mouseX: Double, mouseY: Double): Boolean {
        val layout = secondaryRowLayout()
        if (!layout.hasOverflow) return false
        return mouseX >= MahjongHudToolbarLayout.MARGIN &&
            mouseX < MahjongHudToolbarLayout.MARGIN + layout.viewportWidth &&
            mouseY >= secondaryRowScrollbarTop() &&
            mouseY < secondaryRowScrollbarTop() + MahjongHudToolbarLayout.SCROLLBAR_HEIGHT
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
            renderControls(context, mouseX, mouseY, delta)
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

    /** 左鍵按住任一預覽框時開始拖曳；按鈕點擊完全交由原版流程處理。 */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (model.controlsVisible && button == 0 && secondaryRowEntries.isNotEmpty() && isOverSecondaryRowScrollbar(mouseX, mouseY)) {
            val layout = secondaryRowLayout()
            val thumb = layout.thumb(secondaryRowScroll)
            if (mouseX !in thumb.left.toDouble()..thumb.right.toDouble()) {
                secondaryRowScroll = layout.scrollFromThumb(mouseX - thumb.width / 2.0)
                updateSecondaryRowPositions()
            }
            draggingSecondaryRowScrollbar = true
            secondaryRowDragStartX = mouseX
            secondaryRowDragStartScroll = secondaryRowScroll
            return true
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
        if (draggingSecondaryRowScrollbar && button == 0) {
            secondaryRowScroll = secondaryRowLayout().scrollFromDrag(secondaryRowDragStartScroll, mouseX - secondaryRowDragStartX)
            updateSecondaryRowPositions()
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
        if (button == 0 && draggingSecondaryRowScrollbar) {
            draggingSecondaryRowScrollbar = false
            return true
        }
        if (button == 0 && model.dragging != null) {
            model = model.endDrag()
            clearAndInit()
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /** 第二行範圍內的滾輪輸入轉為水平捲動。 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (model.controlsVisible && secondaryRowEntries.isNotEmpty()) {
            val layout = secondaryRowLayout()
            if (layout.hasOverflow && isInsideSecondaryRowArea(mouseX, mouseY)) {
                secondaryRowScroll = layout.scrollFromWheel(secondaryRowScroll, amount)
                updateSecondaryRowPositions()
                return true
            }
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
            client?.setScreen(
                UnsavedChangesConfirmationScreen(
                    this,
                    { apply(true) },
                    { client?.setScreen(parent) },
                    clientConfigDifferenceText(
                        MahjongClientConfigState(hudLayout = model.baseline),
                        MahjongClientConfigState(hudLayout = model.draft),
                    ),
                ),
            )
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

    /**
     * 繪製全部按鈕的不透明底色與本體；第二行按鈕額外裁切以支援水平捲動。所有按鈕的 tooltip 都延後到
     * 最後才畫，確保不會被之後繪製的按鈕（尤其是垂直距離很近的第二行）蓋住。
     */
    private fun renderControls(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val secondaryButtons = secondaryRowEntries.map { it.first }.toSet()
        secondaryButtons.forEach { it.visible = false }
        children().filterIsInstance<ButtonWidget>().filter { it.visible }.forEach { button ->
            context.fill(button.x, button.y, button.x + button.width, button.y + button.height, EDITOR_BUTTON_BACKING_COLOR)
        }
        super.render(context, mouseX, mouseY, delta)
        secondaryButtons.forEach { it.visible = model.controlsVisible }

        if (secondaryRowEntries.isNotEmpty()) {
            updateSecondaryRowPositions()
            val layout = secondaryRowLayout()
            context.enableScissor(
                MahjongHudToolbarLayout.MARGIN,
                secondaryRowTop(),
                MahjongHudToolbarLayout.MARGIN + layout.viewportWidth,
                secondaryRowTop() + MahjongHudToolbarLayout.BUTTON_HEIGHT,
            )
            secondaryRowEntries.forEach { (button, _) ->
                context.fill(button.x, button.y, button.x + button.width, button.y + button.height, EDITOR_BUTTON_BACKING_COLOR)
                button.render(context, mouseX, mouseY, delta)
            }
            context.disableScissor()
            if (layout.hasOverflow) {
                val scrollbarTop = secondaryRowScrollbarTop()
                context.fill(
                    MahjongHudToolbarLayout.MARGIN,
                    scrollbarTop,
                    MahjongHudToolbarLayout.MARGIN + layout.viewportWidth,
                    scrollbarTop + MahjongHudToolbarLayout.SCROLLBAR_HEIGHT,
                    TOOLBAR_TRACK_COLOR,
                )
                val thumb = layout.thumb(secondaryRowScroll)
                context.fill(thumb.left, scrollbarTop, thumb.right, scrollbarTop + MahjongHudToolbarLayout.SCROLLBAR_HEIGHT, TOOLBAR_THUMB_COLOR)
            }
        }

        toolbarTooltips.entries.firstOrNull { (button, _) -> button.visible && button.isMouseOver(mouseX.toDouble(), mouseY.toDouble()) }
            ?.let { (_, tooltip) -> context.drawTooltip(textRenderer, tooltip, mouseX, mouseY) }
    }

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

    /** Editor 配色與 Z 位移常數；第二行捲動幾何常數見 [MahjongHudToolbarLayout]。 */
    private companion object {
        /** 第二行 scrollbar 軌道色。 */
        const val TOOLBAR_TRACK_COLOR = 0xFF26333D.toInt()

        /** 第二行 scrollbar thumb 色。 */
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

        /** 僅位於每顆 editor 按鈕自身範圍內的不透明底色。 */
        const val EDITOR_BUTTON_BACKING_COLOR = 0xFF101820.toInt()

        /** Editor 控制項高於不同 GUI render layer 中 HUD 文字的 Z 位移。 */
        const val EDITOR_CONTROLS_Z = 400.0

        /** 中心參考線。 */
        const val GUIDE_COLOR = 0x446A7C8C

        /** 標題與 hover 強調色。 */
        const val TITLE_COLOR = 0xFFD54F
    }
}
