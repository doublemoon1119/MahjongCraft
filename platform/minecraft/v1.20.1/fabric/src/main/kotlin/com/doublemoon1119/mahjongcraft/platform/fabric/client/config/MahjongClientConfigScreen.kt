package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.SettingsFooterLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import kotlinx.serialization.json.Json
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Language

/** 原生 MahjongCraft client 設定畫面；只編輯本機設定草稿，不接觸房間規則。 */
class MahjongClientConfigScreen(
    private val parent: Screen?,
    private val configStore: MahjongClientConfigStore,
    private val json: Json,
) : Screen(Text.translatable(MinecraftClientConfigScreenKeys.TITLE)) {
    /** 目前顯示的設定分類。 */
    private var category = Category.GENERAL

    /** 使用者尚未套用的完整設定草稿。 */
    private var draft = configStore.current

    /** 畫面開啟時的權威設定。 */
    private var baseline = configStore.current

    /** 畫面開啟時的 store revision。 */
    private var baselineRevision = configStore.revision

    /** 外部 reload 或指令更新使目前草稿失效。 */
    private var draftStale = false

    /** 最近一次保存失敗的本地化狀態。 */
    private var saveFailed = false

    /** 目前分類的垂直捲動列數。 */
    private var rowScroll = 0

    /** 是否正在拖曳捲動條。 */
    private var draggingScrollbar = false

    /** 拖曳時游標在 thumb 內的垂直偏移。 */
    private var scrollbarGrabOffset = 0.0

    /** 下一個 client tick 是否需要安全重建 widgets。 */
    private var rebuildRequested = false

    /** 套用按鈕，供草稿狀態即時更新。 */
    private var applyButton: ButtonWidget? = null

    /** 復原按鈕，供草稿狀態即時更新。 */
    private var undoButton: ButtonWidget? = null

    /** 重設按鈕，供草稿狀態即時更新。 */
    private var resetButton: ButtonWidget? = null

    override fun init() {
        applyButton = null
        undoButton = null
        resetButton = null
        val bounds = panelBounds()
        if (bounds.compact) {
            val categoryWidth = (bounds.width - PANEL_PADDING * 2 - BOTTOM_BUTTON_GAP) / 2
            addCategoryButton(bounds.left + PANEL_PADDING, bounds.contentTop, categoryWidth, Category.GENERAL)
            addCategoryButton(
                bounds.left + PANEL_PADDING + categoryWidth + BOTTOM_BUTTON_GAP,
                bounds.contentTop,
                categoryWidth,
                Category.DISPLAY,
            )
        } else {
            addCategoryButton(bounds.left + PANEL_PADDING, bounds.contentTop, bounds.sidebarButtonWidth, Category.GENERAL)
            addCategoryButton(
                bounds.left + PANEL_PADDING,
                bounds.contentTop + CATEGORY_BUTTON_GAP,
                bounds.sidebarButtonWidth,
                Category.DISPLAY,
            )
        }
        addFieldButtons(bounds)
        addBottomButtons(bounds)
        refreshButtons()
    }

    /** 設定畫面不暫停整合伺服器或單人遊戲。 */
    override fun shouldPause(): Boolean = false

    /** Esc 在無變更時返回；有未套用草稿時顯示明確的三選項確認畫面。 */
    override fun close() {
        if (draftStale || draft == baseline) {
            client?.setScreen(parent)
        } else {
            client?.setScreen(
                ClientConfigUnsavedChangesScreen(
                    this,
                    { applyDraft(closeAfterSave = true) },
                    { client?.setScreen(parent) },
                ),
            )
        }
    }

    /** 偵測畫面開啟期間由 reload／指令造成的外部設定變更。 */
    override fun tick() {
        super.tick()
        if (rebuildRequested) {
            rebuildRequested = false
            clearAndInit()
            return
        }
        if (configStore.revision != baselineRevision && !draftStale) {
            draftStale = true
            saveFailed = false
            refreshButtons()
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, SCREEN_OVERLAY_COLOR)
        val bounds = panelBounds()
        context.fill(bounds.left, bounds.top, bounds.right, bounds.bottom, PANEL_COLOR)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, bounds.titleY, TITLE_COLOR)
        if (!bounds.compact) {
            context.fill(
                bounds.left + SIDEBAR_WIDTH,
                bounds.contentTop - 8,
                bounds.left + SIDEBAR_WIDTH + 1,
                bounds.bottom - BOTTOM_AREA_HEIGHT,
                DIVIDER_COLOR,
            )
        }
        renderFieldLabels(context, bounds)
        renderStatus(context, bounds)
        renderScrollbar(context, bounds)
        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val maximum = maximumScroll()
        if (maximum > 0 && amount != 0.0 && isInsideFields(mouseX, mouseY, panelBounds())) {
            val direction = if (amount > 0.0) -1 else 1
            rowScroll = (rowScroll + direction).coerceIn(0, maximum)
            rebuild()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && isOverScrollbar(mouseX, mouseY, panelBounds())) {
            val layout = scrollbarLayout(panelBounds())
            draggingScrollbar = true
            scrollbarGrabOffset = layout.grabOffset(mouseY)
            updateScrollFromMouse(mouseY, panelBounds())
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(mouseY, panelBounds())
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /** 建立分類切換按鈕。 */
    private fun addCategoryButton(x: Int, y: Int, buttonWidth: Int, target: Category) {
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(target.translationKey)) {
                category = target
                rowScroll = 0
                rebuild()
            }.dimensions(x, y, buttonWidth, BUTTON_HEIGHT).build().also {
                it.active = category != target
            },
        )
    }

    /** 依分類建立目前可見的欄位控制項。 */
    private fun addFieldButtons(bounds: PanelBounds) {
        val rows = rows()
        val visible = visibleRowCount(bounds)
        rowScroll = rowScroll.coerceIn(0, (rows.size - visible).coerceAtLeast(0))
        rows.drop(rowScroll).take(visible).forEachIndexed { index, row ->
            val y = bounds.fieldsTop + index * FIELD_ROW_HEIGHT
            val message = if (bounds.compact) {
                Text.translatable(row.nameKey).append(": ").append(row.valueText(draft))
            } else {
                row.valueText(draft)
            }
            val button = RestartableMarqueeButtonWidget.builder(message) {
                if (row.onActivate != null) {
                    row.onActivate.invoke()
                } else {
                    row.update?.let { update ->
                        draft = update(draft)
                        saveFailed = false
                        rebuild()
                    }
                }
            }.dimensions(bounds.controlLeft, y, bounds.controlWidth, BUTTON_HEIGHT).build().also {
                it.active = (row.update != null || row.onActivate != null) && !draftStale
                it.tooltip = Tooltip.of(Text.translatable(row.descriptionKey))
            }
            addDrawableChild(button)
        }
    }

    /** 建立固定單行的 Reset to Defaults、Undo、Apply、Done。 */
    private fun addBottomButtons(bounds: PanelBounds) {
        val availableWidth = bounds.width - PANEL_PADDING * 2
        val footer = SettingsFooterLayout.create(
            left = bounds.left + PANEL_PADDING,
            availableWidth = availableWidth,
            preferredResetWidth = RESET_BUTTON_PREFERRED_WIDTH,
            gap = BOTTOM_BUTTON_GAP,
        )
        val y = bounds.bottom - PANEL_PADDING - BUTTON_HEIGHT
        resetButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.RESET_DEFAULTS)) {
                draft = MahjongClientConfigState()
                saveFailed = false
                rebuild()
            }.dimensions(footer.resetX, y, footer.resetWidth, BUTTON_HEIGHT).build(),
        )
        undoButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.UNDO)) {
                undoDraft()
            }.dimensions(footer.undoX, y, footer.actionWidth, BUTTON_HEIGHT).build(),
        )
        applyButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.APPLY)) {
                applyDraft(closeAfterSave = false)
            }.dimensions(footer.applyX, y, footer.actionWidth, BUTTON_HEIGHT).build(),
        )
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.DONE)) {
                if (draftStale || draft == baseline) close() else applyDraft(closeAfterSave = true)
            }.dimensions(footer.doneX, y, footer.actionWidth, BUTTON_HEIGHT).build(),
        )
    }

    /** 原子保存草稿；自動整理偏好變更時才同步伺服器。 */
    private fun applyDraft(closeAfterSave: Boolean): Boolean {
        if (draftStale) return false
        if (draft == baseline) {
            if (closeAfterSave) close()
            return true
        }
        val previous = configStore.current
        return when (configStore.save(draft)) {
            is MahjongClientConfigUpdateResult.Success -> {
                if (previous.autoSortHandEnabled != draft.autoSortHandEnabled && client?.networkHandler != null) {
                    MahjongChannels.setAutoSortHand.sendToServer(json, draft.autoSortHandEnabled)
                }
                baseline = draft
                baselineRevision = configStore.revision
                saveFailed = false
                if (closeAfterSave) close() else rebuild()
                true
            }

            is MahjongClientConfigUpdateResult.Failure -> {
                saveFailed = true
                refreshButtons()
                false
            }
        }
    }

    /** 目前完整草稿，供 HUD editor 在不建立第二份設定來源的情況下承接。 */
    internal fun currentDraft(): MahjongClientConfigState = draft

    /** 將 HUD 配置併入完整草稿並透過既有原子保存流程套用。 */
    internal fun applyHudLayout(layout: MahjongHudLayoutConfig): Boolean {
        draft = draft.copy(hudLayout = layout)
        return applyDraft(closeAfterSave = false)
    }

    /** 依目前草稿、預設值與 revision 更新底部按鈕狀態。 */
    private fun refreshButtons() {
        resetButton?.active = !draftStale && draft != MahjongClientConfigState()
        applyButton?.active = !draftStale && draft != baseline
        undoButton?.active = draftStale || draft != baseline
        val changes = if (!draftStale && draft != baseline) Tooltip.of(clientConfigDifferenceText(baseline, draft)) else null
        applyButton?.tooltip = changes
        undoButton?.tooltip = changes
    }

    /** 復原未套用變更；草稿過期時改以 store 最新權威值為基準。 */
    private fun undoDraft() {
        if (draftStale) {
            baseline = configStore.current
            baselineRevision = configStore.revision
            draftStale = false
        }
        draft = baseline
        saveFailed = false
        rebuild()
    }

    /** 繪製欄位名稱，控制項寬度固定且名稱依實際像素寬度安全截斷。 */
    private fun renderFieldLabels(context: DrawContext, bounds: PanelBounds) {
        if (bounds.compact) return
        val rows = rows()
        rows.drop(rowScroll).take(visibleRowCount(bounds)).forEachIndexed { index, row ->
            val y = bounds.fieldsTop + index * FIELD_ROW_HEIGHT + VANILLA_TEXT_OFFSET_Y
            val left = bounds.left + SIDEBAR_WIDTH + PANEL_PADDING
            val available = bounds.controlLeft - PANEL_PADDING - left
            context.drawTextWithShadow(textRenderer, fitText(Text.translatable(row.nameKey), available), left, y, TEXT_COLOR)
        }
    }

    /** 繪製草稿過期或保存失敗狀態。 */
    private fun renderStatus(context: DrawContext, bounds: PanelBounds) {
        val status = when {
            draftStale -> Text.translatable(MinecraftClientConfigScreenKeys.DRAFT_STALE).formatted(Formatting.RED)
            saveFailed -> Text.translatable(MinecraftClientConfigScreenKeys.SAVE_FAILED).formatted(Formatting.RED)
            else -> return
        }
        context.drawTextWithShadow(
            textRenderer,
            fitText(status, bounds.width - PANEL_PADDING * 2),
            bounds.left + PANEL_PADDING,
            bounds.bottom - BOTTOM_AREA_HEIGHT + STATUS_OFFSET_Y,
            TEXT_COLOR,
        )
    }

    /** 只有欄位超出可見範圍時繪製 scrollbar。 */
    private fun renderScrollbar(context: DrawContext, bounds: PanelBounds) {
        val layout = scrollbarLayout(bounds)
        if (layout.maximumScroll <= 0) return
        val trackLeft = bounds.right - SCROLLBAR_MARGIN
        context.fill(trackLeft, layout.trackTop, trackLeft + SCROLLBAR_WIDTH, layout.trackBottom, SCROLLBAR_TRACK_COLOR)
        context.fill(
            trackLeft,
            layout.thumbTop,
            trackLeft + SCROLLBAR_WIDTH,
            layout.thumbTop + layout.thumbHeight,
            SCROLLBAR_THUMB_COLOR,
        )
    }

    /** 依滑鼠位置更新欄位捲動列。 */
    private fun updateScrollFromMouse(mouseY: Double, bounds: PanelBounds) {
        val layout = scrollbarLayout(bounds)
        if (layout.maximumScroll <= 0) return
        rowScroll = layout.scrollIndexFor(mouseY, scrollbarGrabOffset)
        rebuild()
    }

    /** 建立目前分類的 scrollbar 幾何。 */
    private fun scrollbarLayout(bounds: PanelBounds): ScrollbarLayout = ScrollbarLayout(
        trackTop = bounds.fieldsTop,
        trackBottom = bounds.bottom - BOTTOM_AREA_HEIGHT,
        itemCount = rows().size,
        visibleItemCount = visibleRowCount(bounds),
        scrollIndex = rowScroll,
        minimumThumbHeight = MIN_SCROLLBAR_THUMB_HEIGHT,
    )

    /** 判斷游標是否位於欄位內容區。 */
    private fun isInsideFields(mouseX: Double, mouseY: Double, bounds: PanelBounds): Boolean = mouseX >= bounds.contentLeft &&
        mouseX < bounds.right &&
        mouseY >= bounds.fieldsTop &&
        mouseY < bounds.bottom - BOTTOM_AREA_HEIGHT

    /** 判斷游標是否位於有效 scrollbar。 */
    private fun isOverScrollbar(mouseX: Double, mouseY: Double, bounds: PanelBounds): Boolean = maximumScroll() > 0 &&
        mouseX >= bounds.right - SCROLLBAR_MARGIN &&
        mouseX < bounds.right - SCROLLBAR_MARGIN + SCROLLBAR_WIDTH &&
        mouseY >= bounds.fieldsTop &&
        mouseY < bounds.bottom - BOTTOM_AREA_HEIGHT

    /** 取得目前分類的宣告式欄位。 */
    private fun rows(): List<ConfigRow> = when (category) {
        Category.GENERAL -> listOf(
            ConfigRow(
                MinecraftClientConfigScreenKeys.AUTO_SORT_HAND,
                MinecraftClientConfigScreenKeys.AUTO_SORT_HAND_DESCRIPTION,
                { booleanText(it.autoSortHandEnabled) },
                { it.copy(autoSortHandEnabled = !it.autoSortHandEnabled) },
            ),
        )

        Category.DISPLAY -> listOf(
            ConfigRow(
                MinecraftClientConfigScreenKeys.TILE_LABELS,
                MinecraftClientConfigScreenKeys.TILE_LABELS_DESCRIPTION,
                { booleanText(it.tileLabelsEnabled) },
                { it.copy(tileLabelsEnabled = !it.tileLabelsEnabled) },
            ),
            ConfigRow(
                MinecraftClientConfigScreenKeys.EDIT_HUD_LAYOUT,
                MinecraftClientConfigScreenKeys.EDIT_HUD_LAYOUT_DESCRIPTION,
                { Text.translatable(MinecraftClientConfigScreenKeys.EDIT_HUD_LAYOUT) },
                onActivate = {
                    client?.setScreen(MahjongHudLayoutEditorScreen(this, currentDraft().hudLayout))
                },
            ),
        )
    }

    /** 將 Boolean 轉換成本地化的開關狀態。 */
    private fun booleanText(value: Boolean): Text = Text.translatable(
        if (value) MinecraftClientConfigScreenKeys.ENABLED else MinecraftClientConfigScreenKeys.DISABLED,
    )

    /** 以省略號安全截斷過寬文字。 */
    private fun fitText(text: Text, maximumWidth: Int): OrderedText = when {
        maximumWidth <= 0 -> OrderedText.EMPTY
        textRenderer.getWidth(text) <= maximumWidth -> text.asOrderedText()
        else -> Language.getInstance().reorder(textRenderer.trimToWidth(text, maximumWidth))
    }

    /** 在目前輸入事件完成後安全重建 widgets，避免舊 widget 被重新設為 focus。 */
    private fun rebuild() {
        rebuildRequested = true
    }

    /** 計算依目前解析度限制的中央面板。 */
    private fun panelBounds(): PanelBounds {
        val panelWidth = (width - SCREEN_MARGIN * 2).coerceAtMost(MAX_PANEL_WIDTH)
        val panelHeight = (height - SCREEN_MARGIN * 2).coerceAtMost(MAX_PANEL_HEIGHT)
        val left = (width - panelWidth) / 2
        val top = (height - panelHeight) / 2
        return PanelBounds(left, top, left + panelWidth, top + panelHeight)
    }

    /** 目前面板可容納的完整欄位列數。 */
    private fun visibleRowCount(bounds: PanelBounds): Int = ((bounds.bottom - BOTTOM_AREA_HEIGHT - bounds.fieldsTop) / FIELD_ROW_HEIGHT).coerceAtLeast(1)

    /** 目前分類最大的捲動列數。 */
    private fun maximumScroll(): Int = (rows().size - visibleRowCount(panelBounds())).coerceAtLeast(0)

    /** 設定分類。 */
    private enum class Category(val translationKey: String) {
        /** 一般行為。 */
        GENERAL(MinecraftClientConfigScreenKeys.CATEGORY_GENERAL),

        /** 視覺顯示。 */
        DISPLAY(MinecraftClientConfigScreenKeys.CATEGORY_DISPLAY),
    }

    /** 一列設定的宣告式內容與 immutable updater。 */
    private data class ConfigRow(
        /** 欄位名稱翻譯鍵。 */
        val nameKey: String,
        /** 欄位說明翻譯鍵。 */
        val descriptionKey: String,
        /** 依草稿產生目前值文字。 */
        val valueText: (MahjongClientConfigState) -> Text,
        /** 不可變更新函式；`null` 表示唯讀入口。 */
        val update: ((MahjongClientConfigState) -> MahjongClientConfigState)? = null,
        /** 非設定值切換的入口動作。 */
        val onActivate: (() -> Unit)? = null,
    )

    /** 中央面板邊界。 */
    private data class PanelBounds(
        /** 左邊界。 */
        val left: Int,
        /** 上邊界。 */
        val top: Int,
        /** 右邊界。 */
        val right: Int,
        /** 下邊界。 */
        val bottom: Int,
    ) {
        /** 面板寬度。 */
        val width: Int
            get() = right - left

        /** 標題基準 Y。 */
        val titleY: Int
            get() = top + TITLE_OFFSET_Y

        /** 分類內容起始 Y。 */
        val contentTop: Int
            get() = top + CONTENT_OFFSET_Y

        /** 欄位內容起始 Y。 */
        val fieldsTop: Int
            get() = top + if (compact) COMPACT_FIELDS_OFFSET_Y else FIELDS_OFFSET_Y

        /** 是否改用頂部分類 tab 與單欄設定按鈕。 */
        val compact: Boolean
            get() = width < TWO_COLUMN_MIN_WIDTH

        /** 側欄分類按鈕寬度。 */
        val sidebarButtonWidth: Int
            get() = SIDEBAR_WIDTH - PANEL_PADDING * 2

        /** 設定內容左界。 */
        val contentLeft: Int
            get() = if (compact) left + PANEL_PADDING else left + SIDEBAR_WIDTH + PANEL_PADDING

        /** 設定控制項左界。 */
        val controlLeft: Int
            get() = if (compact) contentLeft else right - controlWidth - PANEL_PADDING

        /** 設定控制項寬度。 */
        val controlWidth: Int
            get() = if (compact) {
                width - PANEL_PADDING * 2
            } else {
                minOf(CONTROL_WIDTH, width - SIDEBAR_WIDTH - PANEL_PADDING * 3)
            }
    }

    /** 畫面尺寸與配色常數。 */
    private companion object {
        /** 面板最大寬度。 */
        const val MAX_PANEL_WIDTH = 560

        /** 面板最大高度。 */
        const val MAX_PANEL_HEIGHT = 320

        /** 面板與螢幕邊緣的最小距離。 */
        const val SCREEN_MARGIN = 12

        /** 面板內距。 */
        const val PANEL_PADDING = 10

        /** 分類側欄寬度。 */
        const val SIDEBAR_WIDTH = 128

        /** 標題相對面板上緣的 Y 位移。 */
        const val TITLE_OFFSET_Y = 12

        /** 分類按鈕相對面板上緣的 Y 位移。 */
        const val CONTENT_OFFSET_Y = 42

        /** 欄位相對面板上緣的 Y 位移。 */
        const val FIELDS_OFFSET_Y = 50

        /** 單欄版面欄位相對面板上緣的 Y 位移。 */
        const val COMPACT_FIELDS_OFFSET_Y = 70

        /** 低於此面板寬度時改用單欄版面。 */
        const val TWO_COLUMN_MIN_WIDTH = 400

        /** 欄位列高。 */
        const val FIELD_ROW_HEIGHT = 30

        /** 原版按鈕高度。 */
        const val BUTTON_HEIGHT = 20

        /** 分類按鈕垂直間距。 */
        const val CATEGORY_BUTTON_GAP = 25

        /** 欄位控制項寬度。 */
        const val CONTROL_WIDTH = 180

        /** 底部操作區高度。 */
        const val BOTTOM_AREA_HEIGHT = 52

        /** 狀態訊息在底部保留區中的 Y 位移。 */
        const val STATUS_OFFSET_Y = 2

        /** Reset to Defaults 偏好寬度。 */
        const val RESET_BUTTON_PREFERRED_WIDTH = 104

        /** 底部按鈕間距。 */
        const val BOTTOM_BUTTON_GAP = 6

        /** 原版按鈕文字垂直位移。 */
        const val VANILLA_TEXT_OFFSET_Y = 6

        /** Scrollbar 與右邊界距離。 */
        const val SCROLLBAR_MARGIN = 7

        /** Scrollbar 寬度。 */
        const val SCROLLBAR_WIDTH = 3

        /** Scrollbar thumb 最小高度。 */
        const val MIN_SCROLLBAR_THUMB_HEIGHT = 12

        /** 全畫面遮罩色。 */
        const val SCREEN_OVERLAY_COLOR = 0x88000000.toInt()

        /** 面板背景色。 */
        const val PANEL_COLOR = 0xD0222B3A.toInt()

        /** 側欄分隔線色。 */
        const val DIVIDER_COLOR = 0x66708088

        /** Scrollbar 軌道色。 */
        const val SCROLLBAR_TRACK_COLOR = 0x554A5566

        /** Scrollbar thumb 色。 */
        const val SCROLLBAR_THUMB_COLOR = 0xFFD0D5DD.toInt()

        /** 標題色。 */
        const val TITLE_COLOR = 0xFFD54F

        /** 一般文字色。 */
        const val TEXT_COLOR = 0xFFFFFF
    }
}

/** Client Config Screen 離開時使用的三選項未保存變更確認畫面。 */
private class ClientConfigUnsavedChangesScreen(
    private val settings: MahjongClientConfigScreen,
    private val apply: () -> Unit,
    private val discard: () -> Unit,
) : Screen(Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_UNSAVED_TITLE)) {
    /** 建立套用、放棄與繼續編輯按鈕。 */
    override fun init() {
        val buttonWidth = minOf(160, width - 24)
        val left = (width - buttonWidth) / 2
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.APPLY_AND_BACK)) { apply() }
                .dimensions(left, height / 2, buttonWidth, 20).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.DISCARD_CHANGES)) { discard() }
                .dimensions(left, height / 2 + 24, buttonWidth, 20).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.CONTINUE_EDITING)) { client?.setScreen(settings) }
                .dimensions(left, height / 2 + 48, buttonWidth, 20).build(),
        )
    }

    /** 確認畫面不暫停遊戲。 */
    override fun shouldPause(): Boolean = false

    /** Esc 返回設定畫面，避免無聲放棄草稿。 */
    override fun close() {
        client?.setScreen(settings)
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
