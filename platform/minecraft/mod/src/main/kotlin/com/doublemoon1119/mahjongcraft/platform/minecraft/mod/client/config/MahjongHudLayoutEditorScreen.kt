package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.CycleButtonInput
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOption
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOverlay
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.MahjongScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.SettingsFooterButtons
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.UnsavedChangesConfirmationScreen
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/**
 * 原生 HUD 位置編輯器；拖曳只修改草稿，套用時才交由父設定畫面原子保存。
 *
 * 這個畫面只負責原版 widget 生命週期與繪製：全部狀態轉換（拖曳、選取、草稿比較）委派給
 * [MahjongHudLayoutEditorModel]，工具列與底部按鈕列的排列委派給 [MahjongHudToolbarLayout]，
 * 兩者都不依賴 Minecraft 型別，因此可以直接以 JVM 測試驗證。
 *
 * 工具列每個控制項上方有標籤。HUD 以下拉清單選擇；預覽情境只有操作面板有，編輯其他 HUD 時按鈕留在原位但停用，提示框說明原因。
 *
 * 選取中的 HUD 以真實內容的半透明預覽畫在草稿位置（見 [MahjongHudPreviewRenderer]），並在外框旁標示名稱與位置。
 *
 * @property parent 開啟這個編輯器的設定畫面，套用草稿與返回都交由它處理。
 * @param initialLayout 開啟編輯器時的配置，作為草稿與復原基準。
 * @property automaticControlLabels 自動操作狀態面板要呈現的項目名稱，由父畫面決定內容。
 * @property previewRenderer 以範例內容畫出真實 HUD 並量測其尺寸。
 */
class MahjongHudLayoutEditorScreen(
    private val parent: MahjongClientConfigScreen,
    initialLayout: MahjongHudLayoutConfig,
    private val automaticControlLabels: List<Component>,
    private val previewRenderer: MahjongHudPreviewRenderer,
) : MahjongScreen(Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_TITLE)) {
    /** 編輯器的全部可測試狀態；拖曳、選取、草稿與控制項顯示都由它決定。 */
    private var model = MahjongHudLayoutEditorModel(baseline = initialLayout)

    /** 最近一次保存是否失敗。 */
    private var saveFailed = false

    /** 套用按鈕。 */
    private var applyButton: Button? = null

    /** 復原按鈕。 */
    private var undoButton: Button? = null

    /** 重設配置按鈕。 */
    private var resetButton: Button? = null

    /** 目前的工具列與底部按鈕列版面。 */
    private var toolbar = MahjongHudToolbarLayout(screenWidth = 0, screenHeight = 0)

    /** HUD 選擇器的展開清單。 */
    private val dropdown = DropdownOverlay()

    /** 預覽情境按鈕；編輯沒有情境的 HUD 時停用。 */
    private var scenarioButton: Button? = null

    /**
     * 按鈕各自的 tooltip 內容（每個元素一行），於 [renderControls] 結尾統一補畫在最上層；停用的按鈕也顯示，
     * 讓玩家看得到停用的原因。用多行清單而不是單一 Text 內嵌 `\n`，因為 [GuiGraphics.renderTooltip] 的單行版本
     * 不會把 `\n` 拆成新的一行，只會把它當成一個缺字字元畫出來。
     */
    private val toolbarTooltips = mutableMapOf<Button, List<Component>>()

    /** Editor 不暫停單人遊戲或 integrated server。 */
    override fun isPauseScreen(): Boolean = false

    /** 建立工具列控制項與重設、復原、套用與返回按鈕。 */
    override fun init() {
        dropdown.close()
        toolbar = MahjongHudToolbarLayout(screenWidth = width, screenHeight = height)
        addToolbarButtons()
        resetButton = addFooterButton(SettingsFooterButtons.RESET, Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_RESET)) {
            model = model.reset()
            saveFailed = false
            refreshButtons()
        }
        undoButton = addFooterButton(SettingsFooterButtons.UNDO, Component.translatable(MinecraftClientConfigScreenKeys.UNDO)) {
            model = model.undo()
            saveFailed = false
            refreshButtons()
        }
        applyButton = addFooterButton(SettingsFooterButtons.APPLY, Component.translatable(MinecraftClientConfigScreenKeys.APPLY)) {
            apply(returnAfterSave = false)
        }
        addFooterButton(SettingsFooterButtons.DONE, Component.translatable(MinecraftClientConfigScreenKeys.BACK)) { requestReturn() }
        refreshButtons()
        updateControlVisibility()
    }

    /**
     * 加入底部按鈕。
     *
     * @param index 按鈕順序，見 [SettingsFooterButtons]。
     * @param label 按鈕文字。
     * @param action 按下後的行為。
     * @return 已加入畫面的按鈕。
     */
    private fun addFooterButton(index: Int, label: Component, action: () -> Unit): Button {
        val area = toolbar.footerButton(index)
        return addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(label) { action() }
                .dimensions(area.x, area.y, area.width, area.height)
                .build(),
        )
    }

    /** 建立工具列的 HUD 選擇器、預覽情境、其他 HUD 的顯示方式與隱藏控制項四個控制項。 */
    private fun addToolbarButtons() {
        toolbarTooltips.clear()
        addRenderableWidget(hudSelectorButton(toolbar.control(MahjongHudToolbarLayout.HUD)))
        scenarioButton = addRenderableWidget(scenarioSelectorButton(toolbar.control(MahjongHudToolbarLayout.SCENARIO)))
        addRenderableWidget(visibilitySelectorButton(toolbar.control(MahjongHudToolbarLayout.VISIBILITY)))
        addRenderableWidget(hideControlsButtonWidget(toolbar.control(MahjongHudToolbarLayout.HIDE_CONTROLS)))
    }

    /**
     * 目前編輯的 HUD 選擇器，點擊展開全部 HUD 的清單。
     *
     * @param area 控制項範圍。
     * @return 選擇器按鈕。
     */
    private fun hudSelectorButton(area: MahjongHudToolbarLayout.Area): Button {
        lateinit var button: RestartableMarqueeButtonWidget
        button = RestartableMarqueeButtonWidget.builder(Component.translatable(model.selectedElement.translationKey)) {
            val entries = HudElement.entries
            dropdown.toggle(
                key = HUD_SELECTOR_KEY,
                anchor = button,
                options = entries.map { DropdownOption(label = Component.translatable(it.translationKey)) },
                selectedIndex = entries.indexOf(model.selectedElement),
            ) { index ->
                model = model.selectElement(entries[index])
                rebuildWidgets()
            }
        }.dimensions(area.x, area.y, area.width, area.height).build()
        button.selectorExpanded = { dropdown.isOpenFor(HUD_SELECTOR_KEY) }
        toolbarTooltips[button] = ControlTooltip.optionLines(
            description = Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_HUD_DESCRIPTION),
            options = HudElement.entries.map { ControlTooltip.Option(label = Component.translatable(it.translationKey), current = it == model.selectedElement) },
        )
        return button
    }

    /**
     * 循環切換操作面板的代表性內容情境；編輯其他 HUD 時停用，提示框說明沒有情境可切換。
     *
     * @param area 控制項範圍。
     * @return 情境按鈕。
     */
    private fun scenarioSelectorButton(area: MahjongHudToolbarLayout.Area): Button {
        val button = RestartableMarqueeButtonWidget.builder(Component.translatable(model.scenario.translationKey)) {
            val entries = HudPreviewScenario.entries
            model = model.selectScenario(CycleButtonInput.next(options = entries, current = model.scenario))
            rebuildWidgets()
        }.pressesOnSecondaryClick().dimensions(area.x, area.y, area.width, area.height).build()
        val description = Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_SCENARIO_DESCRIPTION)
        button.active = model.selectedElement.hasScenarios
        toolbarTooltips[button] = if (button.active) {
            selectionTooltip(description, HudPreviewScenario.entries.map { Component.translatable(it.translationKey) to (it == model.scenario) })
        } else {
            listOf(
                description.copy().withStyle(ChatFormatting.GRAY),
                Component.empty(),
                Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_SCENARIO_UNAVAILABLE, Component.translatable(model.selectedElement.translationKey)).withStyle(ChatFormatting.RED),
            )
        }
        return button
    }

    /**
     * 循環切換非作用中 HUD 的預覽方式；與目前編輯哪個 HUD 無關，屬於全域設定。
     *
     * @param area 控制項範圍。
     * @return 切換其他 HUD 預覽方式的按鈕。
     */
    private fun visibilitySelectorButton(area: MahjongHudToolbarLayout.Area): Button {
        val button = RestartableMarqueeButtonWidget.builder(Component.translatable(model.otherHudVisibility.translationKey)) {
            val entries = HudPreviewVisibility.entries
            model = model.selectVisibility(CycleButtonInput.next(options = entries, current = model.otherHudVisibility))
            rebuildWidgets()
        }.pressesOnSecondaryClick().dimensions(area.x, area.y, area.width, area.height).build()
        toolbarTooltips[button] = selectionTooltip(
            description = Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_VISIBILITY_DESCRIPTION),
            options = HudPreviewVisibility.entries.map { Component.translatable(it.translationKey) to (it == model.otherHudVisibility) },
        )
        return button
    }

    /**
     * 全域的隱藏控制項按鈕；與目前編輯哪個 HUD 無關。
     *
     * @param area 控制項範圍。
     * @return 點擊後隱藏編輯控制項的按鈕。
     */
    private fun hideControlsButtonWidget(area: MahjongHudToolbarLayout.Area): Button = RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_HIDE_CONTROLS)) {
        model = model.withControlsHidden(true)
        updateControlVisibility()
    }.dimensions(area.x, area.y, area.width, area.height).build()

    /**
     * 建立循環切換按鈕的提示（每個元素一行）：說明、全部選項與目前選項，以及操作提示。
     *
     * @param description 控制項說明。
     * @param options 選項名稱與是否選取。
     * @return 提示框的每一行。
     */
    private fun selectionTooltip(description: Component, options: List<Pair<Component, Boolean>>): List<Component> = ControlTooltip.optionLines(
        description = description,
        options = options.map { (label, selected) -> ControlTooltip.Option(label = label, current = selected) },
        hints = CycleButtonInput.hintTexts(optionCount = options.size),
    )

    /** Esc 使用與返回按鈕相同的未保存變更保護。 */
    override fun onClose() {
        if (model.controlsManuallyHidden) {
            model = model.withControlsHidden(false)
            updateControlVisibility()
        } else {
            requestReturn()
        }
    }

    /** 繪製背景、參考線與全部可拖曳的 HUD 預覽。 */
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        measurePreviews()
        context.fill(0, 0, width, height, SCREEN_OVERLAY_COLOR)
        context.fill(width / 2, 24, width / 2 + 1, height - 34, GUIDE_COLOR)
        context.fill(0, height / 2, width, height / 2 + 1, GUIDE_COLOR)
        context.drawCenteredString(font, title, width / 2, 10, TITLE_COLOR)
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
            context.drawCenteredString(
                font,
                Component.translatable(MinecraftClientConfigScreenKeys.SAVE_FAILED).withStyle(ChatFormatting.RED),
                width / 2,
                toolbar.footerTop - SAVE_FAILED_GAP,
                0xFFFFFF,
            )
        }
        if (model.controlsVisible) {
            context.pose().pushPose()
            context.pose().translate(0.0, 0.0, EDITOR_CONTROLS_Z)
            renderControls(context, mouseX, mouseY, delta)
            context.pose().popPose()
        } else if (model.controlsManuallyHidden && model.dragging == null) {
            context.drawCenteredString(
                font,
                Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_SHOW_CONTROLS_HINT),
                width / 2,
                height - 12,
                0xB0B0B0,
            )
        }
        renderTrimmedPreviewTooltip(context, mouseX, mouseY)
    }

    /**
     * 外框預覽太窄而讓名稱被截斷時，補一個完整名稱與位置的 tooltip；選取中的元素已在外框旁標示完整內容，不另外顯示。
     *
     * 只在沒有拖曳、且游標不在任何工具列按鈕上時顯示，避免與按鈕本身的 tooltip 疊在一起。
     *
     * @param context 畫面繪製環境。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     */
    private fun renderTrimmedPreviewTooltip(context: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (model.dragging != null) return
        if (model.controlsVisible && toolbarTooltips.keys.any { it.visible && it.isHovered(mouseX, mouseY) }) return
        val element = model.hitTest(
            mouseX = mouseX.toDouble(),
            mouseY = mouseY.toDouble(),
            screenWidth = width,
            screenHeight = height,
        ) ?: return
        if (element == model.selectedElement) return
        val name = Component.translatable(element.translationKey)
        val position = positionText(element)
        if (font.width(name) <= previewTextWidth(bounds(element))) return
        context.renderComponentTooltip(font, listOf(name, Component.literal(position)), mouseX, mouseY)
    }

    /** 左鍵按住任一預覽框時開始拖曳；按鈕點擊完全交由原版流程處理。 */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dropdown.mouseClicked(font, height, mouseX, mouseY, button)) return true
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
        if (dropdown.isOpen) return true
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
        if (button == 0 && model.dragging != null) {
            model = model.endDrag()
            rebuildWidgets()
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /** HUD 清單展開時在清單內捲動。 */
    override fun mouseWheelScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (dropdown.mouseScrolled(font, height, amount)) return true
        return super.mouseWheelScrolled(mouseX, mouseY, amount)
    }

    /** HUD 清單展開時由清單處理按鍵，Esc 只收合清單。 */
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dropdown.keyPressed(font, height, keyCode)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /**
     * 透過父畫面的完整草稿保存流程套用 HUD 配置。
     *
     * @param returnAfterSave 沒有變更或保存成功時是否返回父畫面。
     */
    private fun apply(returnAfterSave: Boolean) {
        if (!model.hasUnsavedChanges) {
            if (returnAfterSave) minecraft?.setScreen(parent)
            return
        }
        if (parent.applyHudLayout(model.draft)) {
            model = model.markApplied()
            saveFailed = false
            refreshButtons()
            if (returnAfterSave) minecraft?.setScreen(parent)
        } else {
            saveFailed = true
        }
    }

    /** 無變更時直接返回；有變更時開啟三選項確認畫面。 */
    private fun requestReturn() {
        if (!model.hasUnsavedChanges) {
            minecraft?.setScreen(parent)
        } else {
            minecraft?.setScreen(
                UnsavedChangesConfirmationScreen(
                    previousScreen = this,
                    onApply = { apply(true) },
                    onDiscard = { minecraft?.setScreen(parent) },
                    changesPreview = clientConfigDifferenceText(
                        from = MahjongClientConfigState(hudLayout = model.baseline),
                        to = MahjongClientConfigState(hudLayout = model.draft),
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
            Tooltip.create(
                clientConfigDifferenceText(
                    from = MahjongClientConfigState(hudLayout = model.baseline),
                    to = MahjongClientConfigState(hudLayout = model.draft),
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
        if (!visible) dropdown.close()
        children().filterIsInstance<Button>().forEach { it.visible = visible }
    }

    /**
     * 繪製控制項上方的標籤、全部按鈕的不透明底色與本體，以及展開的 HUD 清單。按鈕的 tooltip 延後到最後才畫，
     * 確保不會被之後繪製的按鈕蓋住；HUD 清單展開時不顯示 tooltip。
     *
     * @param context 畫面繪製環境。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param delta 本次繪製使用的 tick 內插比例。
     */
    private fun renderControls(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        listOf(
            MahjongHudToolbarLayout.HUD to MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_HUD,
            MahjongHudToolbarLayout.SCENARIO to MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_SCENARIO,
            MahjongHudToolbarLayout.VISIBILITY to MinecraftClientConfigScreenKeys.HUD_LAYOUT_SELECTOR_VISIBILITY,
        ).forEach { (index, key) ->
            val area = toolbar.control(index)
            val enabled = index != MahjongHudToolbarLayout.SCENARIO || scenarioButton?.active == true
            val caption = trimPreviewText(text = Component.translatable(key).string, maxWidth = area.width, widthOf = font::width)
            context.drawString(font, Component.literal(caption), area.x, toolbar.captionY(index), if (enabled) CAPTION_COLOR else DISABLED_CAPTION_COLOR)
        }
        children().filterIsInstance<Button>().filter { it.visible }.forEach { button ->
            context.fill(button.x, button.y, button.x + button.width, button.y + button.height, EDITOR_BUTTON_BACKING_COLOR)
        }
        renderWidgets(context, dropdown.pointer(mouseX), dropdown.pointer(mouseY), delta)
        if (!dropdown.isOpen) {
            toolbarTooltips.entries.firstOrNull { (button, _) -> button.visible && button.isHovered(mouseX, mouseY) }
                ?.let { (_, tooltip) -> context.renderComponentTooltip(font, tooltip, mouseX, mouseY) }
        }
        dropdown.render(context, font, height, mouseX, mouseY)
    }

    /**
     * 判斷游標是否在按鈕範圍內；停用的按鈕也算，讓停用原因的提示框可以顯示。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標在按鈕範圍內時為 true。
     */
    private fun Button.isHovered(mouseX: Int, mouseY: Int): Boolean = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    /** 以範例內容量測每個 HUD 的實際尺寸，讓預覽框與實際 HUD 同寬同高；無法量測的 HUD 沿用模型的內建預設尺寸。 */
    private fun measurePreviews() {
        HudElement.entries.forEach { element ->
            val size = previewRenderer.size(
                element = element,
                scenario = model.scenario,
                automaticControlLabels = automaticControlLabels,
                screenWidth = width,
                screenHeight = height,
            ) ?: return@forEach
            if (model.measuredSizes[element] != size) model = model.withMeasuredSize(element, size)
        }
    }

    /**
     * 預覽框內可用於文字的寬度，左右各留一點內距。
     *
     * @param bounds 預覽框邊界。
     * @return 扣除左右內距的文字寬度，至少為一像素。
     */
    private fun previewTextWidth(bounds: MahjongHudBounds): Int = (bounds.width - PREVIEW_TEXT_PADDING * 2).coerceAtLeast(1)

    /**
     * 依可用寬度截斷預覽框內的文字。
     *
     * @param text 原始文字。
     * @param maxWidth 可用的像素寬度。
     * @return 適合預覽框寬度的純文字。
     */
    private fun trimToPreview(text: Component, maxWidth: Int): Component = Component.literal(
        trimPreviewText(
            text = text.string,
            maxWidth = maxWidth,
            widthOf = font::width,
        ),
    )

    /**
     * 預覽框內顯示的位置百分比；只可調整垂直位置的元素不顯示 X。
     *
     * @param element 欲顯示位置的 HUD 元件。
     * @return 可調整軸的位置百分比文字。
     */
    private fun positionText(element: HudElement): String = when (element) {
        HudElement.DECISION -> "Y ${percent(model.draft.decisionPanelY)}%"
        HudElement.COMPACT -> "X ${percent(model.draft.compactPromptX)}%  Y ${percent(model.draft.compactPromptY)}%"
        HudElement.ANALYSIS -> "Y ${percent(model.draft.discardAnalysisY)}%"
        HudElement.AUTOMATIC_CONTROL ->
            "X ${percent(model.draft.automaticControlStatusX)}%  Y ${percent(model.draft.automaticControlStatusY)}%"
    }

    /**
     * 將比例轉為整數百分比。
     *
     * @param value 位置比例。
     * @return 四捨五入後的整數百分比。
     */
    private fun percent(value: Double): Int = (value * 100).roundToInt()

    /**
     * 取得一個預覽框目前的完整 bounds。
     *
     * @param element 欲查詢的 HUD 元件。
     * @return 依目前草稿、畫面尺寸與量測值計算的邊界。
     */
    private fun bounds(element: HudElement): MahjongHudBounds = model.bounds(
        element = element,
        screenWidth = width,
        screenHeight = height,
    )

    /**
     * 繪製與正式 HUD 視覺語言一致的拖曳預覽。
     *
     * @param context 畫面繪製環境。
     * @param element 欲繪製的 HUD 元件。
     * @param bounds 預覽框邊界。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     */
    private fun renderPreview(
        context: GuiGraphics,
        element: HudElement,
        bounds: MahjongHudBounds,
        mouseX: Int,
        mouseY: Int,
    ) {
        val hovered = bounds.contains(mouseX.toDouble(), mouseY.toDouble())
        previewRenderer.render(
            context = context,
            element = element,
            scenario = model.scenario,
            layout = model.draft,
            automaticControlLabels = automaticControlLabels,
            opacity = if (hovered || model.dragging == element) PREVIEW_HOVER_OPACITY else PREVIEW_OPACITY,
        )
        context.renderOutline(bounds.left, bounds.top, bounds.width, bounds.height, SELECTED_BORDER_COLOR)
        renderPreviewLabel(context, element, bounds, hovered)
    }

    /**
     * 在預覽外框上方標示名稱與位置；上方空間不足時改標在下方。
     *
     * @param context 畫面繪製環境。
     * @param element 欲標示的 HUD 元件。
     * @param bounds 預覽框邊界。
     * @param hovered 游標是否位於預覽框內，影響文字顏色。
     */
    private fun renderPreviewLabel(context: GuiGraphics, element: HudElement, bounds: MahjongHudBounds, hovered: Boolean) {
        val label = Component.translatable(element.translationKey).append("  ").append(positionText(element))
        val labelWidth = (font.width(label) + PREVIEW_LABEL_PADDING * 2).coerceAtMost(width)
        val labelHeight = font.lineHeight + PREVIEW_LABEL_PADDING * 2
        val left = (bounds.left + bounds.width / 2 - labelWidth / 2).coerceIn(0, (width - labelWidth).coerceAtLeast(0))
        val above = bounds.top - PREVIEW_LABEL_GAP - labelHeight
        val top = (if (above >= 0) above else bounds.bottom + PREVIEW_LABEL_GAP).coerceIn(0, (height - labelHeight).coerceAtLeast(0))
        context.fill(left, top, left + labelWidth, top + labelHeight, PREVIEW_LABEL_BACKGROUND)
        context.drawCenteredString(
            font,
            label,
            left + labelWidth / 2,
            top + PREVIEW_LABEL_PADDING,
            if (hovered) TITLE_COLOR else 0xFFFFFF,
        )
    }

    /**
     * 以淡暗背景、外框與置中名稱表示非作用中的 HUD。
     *
     * @param context 畫面繪製環境。
     * @param element 欲繪製的 HUD 元件。
     * @param bounds 預覽框邊界。
     */
    private fun renderOutline(context: GuiGraphics, element: HudElement, bounds: MahjongHudBounds) {
        context.fill(bounds.left, bounds.top, bounds.right, bounds.bottom, OUTLINE_BACKGROUND_COLOR)
        context.renderOutline(bounds.left, bounds.top, bounds.width, bounds.height, OUTLINE_COLOR)
        val label = trimToPreview(Component.translatable(element.translationKey), previewTextWidth(bounds))
        context.drawCenteredString(font, label, bounds.left + bounds.width / 2, bounds.top + 7, OUTLINE_TEXT_COLOR)
    }

    /** Editor 配色與 Z 位移常數；工具列版位常數見 [MahjongHudToolbarLayout]。 */
    private companion object {
        /** HUD 選擇器展開清單的識別值。 */
        const val HUD_SELECTOR_KEY = "hud"

        /** 保存失敗訊息與底部按鈕列之間的距離。 */
        const val SAVE_FAILED_GAP = 12

        /** 工具列標籤的文字顏色。 */
        const val CAPTION_COLOR = 0xBBBBBB

        /** 停用控制項的標籤文字顏色。 */
        const val DISABLED_CAPTION_COLOR = 0x777777

        /** 預覽框內文字與左右邊界之間的內距。 */
        const val PREVIEW_TEXT_PADDING = 2

        /** 全畫面半透明遮罩。 */
        const val SCREEN_OVERLAY_COLOR = 0x88000000.toInt()

        /** 選取中 HUD 的真實內容預覽不透明度。 */
        const val PREVIEW_OPACITY = 0.6f

        /** 游標停在預覽上或正在拖曳時的不透明度。 */
        const val PREVIEW_HOVER_OPACITY = 0.85f

        /** 預覽標籤背景。 */
        const val PREVIEW_LABEL_BACKGROUND = 0xCC101820.toInt()

        /** 預覽標籤文字與背景邊緣的內距。 */
        const val PREVIEW_LABEL_PADDING = 2

        /** 預覽標籤與外框之間的間距。 */
        const val PREVIEW_LABEL_GAP = 2

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
