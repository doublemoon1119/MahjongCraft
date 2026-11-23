package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.BuiltInMinecraftAutomaticControlIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.AutomaticControlDisplayResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutoSortHandPreferenceService
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutoSortHandPreferenceUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutomaticControlDraftSession
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutomaticControlResultKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutomaticControlSubmitResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutomaticControlUpdateCoordinator
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.MahjongScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.MarqueeState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.SettingsFooterButtons
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.TabStripLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.TabWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.UnsavedChangesConfirmationScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 原生 MahjongCraft client 設定畫面；分開編輯本機設定與本局自動操作草稿，不接觸房間規則。
 *
 * @property parent 返回時顯示的上層畫面。
 * @property configStore 本機客戶端設定儲存區。
 * @property automaticCoordinator 本局自動操作更新協調器。
 * @property displayResolver 自動操作顯示解析器。
 * @property preferenceService 手牌偏好保存服務。
 * @property hudPreviewRenderer HUD 位置編輯器的真實 HUD 預覽繪製。
 */
class MahjongClientConfigScreen(
    private val parent: Screen?,
    private val configStore: MahjongClientConfigStore,
    private val automaticCoordinator: ClientAutomaticControlUpdateCoordinator,
    private val displayResolver: AutomaticControlDisplayResolver,
    private val preferenceService: ClientAutoSortHandPreferenceService,
    private val hudPreviewRenderer: MahjongHudPreviewRenderer,
) : MahjongScreen(Component.translatable(MinecraftClientConfigScreenKeys.TITLE)) {
    /** 伺服器權威狀態之外的畫面專用本局草稿。 */
    private val automaticDraft = ClientAutomaticControlDraftSession(automaticCoordinator.snapshot())

    /** 最近一次送出／確認失敗的本局控制狀態。 */
    private var automaticError: AutomaticControlError? = null

    /** 本機保存成功但偏好封包未送出。 */
    private var preferenceSyncFailed = false

    /** 目前顯示的設定分類。 */
    private var category = Category.HUD

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

    /** 目前分類的垂直捲動狀態。 */
    private val rowScroll = ScrollState()

    /** 分類頁籤列向左捲動的像素數。 */
    private var tabScroll = 0

    /** 底部狀態文字的跑馬燈進度。 */
    private val statusMarquee = MarqueeState()

    /** 下次建立元件時是否捲動頁籤列，讓選中的頁籤完整顯示。 */
    private var revealSelectedTab = true

    /** 下一個 client tick 是否需要安全重建 widgets。 */
    private var rebuildRequested = false

    /** 套用按鈕，供草稿狀態即時更新。 */
    private var applyButton: Button? = null

    /** 復原按鈕，供草稿狀態即時更新。 */
    private var undoButton: Button? = null

    /** 重設按鈕，供草稿狀態即時更新。 */
    private var resetButton: Button? = null

    /** 完成按鈕，等待 ACK 時禁止離開。 */
    private var doneButton: Button? = null

    override fun init() {
        applyButton = null
        undoButton = null
        resetButton = null
        doneButton = null
        val layout = layout()
        addTabs(layout)
        addFieldButtons(layout)
        addBottomButtons(layout)
        refreshButtons()
    }

    /** 設定畫面不暫停整合伺服器或單人遊戲。 */
    override fun isPauseScreen(): Boolean = false

    /** Esc 在無變更時返回；有未套用草稿時顯示明確的三選項確認畫面。 */
    override fun onClose() {
        if (automaticDraft.state().pending) return
        if (!hasChanges()) {
            minecraft?.setScreen(parent)
        } else {
            minecraft?.setScreen(
                UnsavedChangesConfirmationScreen(
                    previousScreen = this,
                    onApply = {
                        minecraft?.setScreen(this)
                        applyDraft(closeAfterSave = true)
                    },
                    onDiscard = { minecraft?.setScreen(parent) },
                    changesPreview = differenceText(),
                ),
            )
        }
    }

    /** 偵測畫面開啟期間由 reload／指令造成的外部設定變更。 */
    override fun tick() {
        super.tick()
        if (rebuildRequested) {
            rebuildRequested = false
            rebuildWidgets()
            return
        }
        if (configStore.revision != baselineRevision && !draftStale) {
            draftStale = true
            saveFailed = false
            refreshButtons()
        }
        val oldRemote = automaticDraft.state()
        automaticDraft.applySnapshot(automaticCoordinator.snapshot())
        if (oldRemote != automaticDraft.state()) rebuild()
        automaticDraft.state().pendingRequestId?.let { requestId ->
            automaticCoordinator.takeCompletion(requestId)?.let { completion ->
                val result = automaticDraft.applyCompletion(completion)
                automaticError = if (result?.accepted == true) null else completion.result.toAutomaticControlError()
                if (result?.closeAfterAcceptance == true) {
                    minecraft?.setScreen(parent)
                    return
                }
                rebuild()
            }
        }
        refreshButtons()
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderScreenBackground(context, mouseX, mouseY, delta)
        val layout = layout()
        ScreenRendering.title(context, font, title, width)
        ScreenRendering.tabLine(context, tabStrip(layout))
        val hoveredLabel = renderFieldLabels(context, layout, mouseX, mouseY)
        renderStatus(context, layout, mouseX, mouseY)
        VerticalScrollbar.render(context, layout.scrollbarLeft, layout.scrollbar(rows().size, rowScroll.index))
        renderWidgets(context, mouseX, mouseY, delta)
        hoveredLabel?.let { context.renderTooltip(font, it, mouseX, mouseY) }
    }

    override fun mouseWheelScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val layout = layout()
        val strip = tabStrip(layout)
        if (strip.contains(mouseX, mouseY) && strip.maximumScroll > 0) {
            val next = strip.scrollByWheel(amount)
            if (next != tabScroll) {
                tabScroll = next
                rebuild()
            }
            return true
        }
        if (rowScroll.scrollBy(amount, layout.scrollbar(rows().size, rowScroll.index).maximumScroll)) {
            rebuild()
            return true
        }
        return super.mouseWheelScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val layout = layout()
        val scrollbar = layout.scrollbar(rows().size, rowScroll.index)
        if (button == 0 && scrollbar.maximumScroll > 0 && layout.isOverScrollbar(mouseX, mouseY)) {
            if (rowScroll.beginDrag(mouseY, scrollbar)) rebuild()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (rowScroll.dragging && button == 0) {
            if (rowScroll.dragTo(mouseY, layout().scrollbar(rows().size, rowScroll.index))) rebuild()
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        rowScroll.endDrag()
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /**
     * 建立分類頁籤。
     *
     * @param layout 設定頁版面。
     */
    private fun addTabs(layout: FormScreenLayout) {
        if (revealSelectedTab) {
            tabScroll = tabStrip(layout).scrollRevealing(category.ordinal)
            revealSelectedTab = false
        }
        val strip = tabStrip(layout)
        tabScroll = strip.effectiveScroll
        Category.entries.forEachIndexed { index, entry ->
            val selected = entry == category
            addRenderableWidget(
                TabWidget(
                    x = strip.tabX(index),
                    y = strip.tabY(selected),
                    width = strip.tabWidth,
                    height = strip.tabHeight(selected),
                    message = Component.translatable(entry.translationKey),
                    selected = selected,
                    clipLeft = strip.left,
                    clipRight = strip.right,
                    onSelect = {
                        category = entry
                        revealSelectedTab = true
                        rowScroll.reset()
                        rebuild()
                    },
                ),
            )
        }
    }

    /**
     * 依分類建立目前可見的欄位控制項：開關靠右、寬度固定；沒有對應值的動作佔滿整列。
     *
     * @param layout 設定頁版面。
     */
    private fun addFieldButtons(layout: FormScreenLayout) {
        val rows = rows()
        rowScroll.clamp(rows.size - layout.visibleFieldCount)
        rows.drop(rowScroll.index).take(layout.visibleFieldCount).forEachIndexed { index, row ->
            val y = layout.fieldControlY(layout.fieldRowTop(index))
            val active = !draftStale && !automaticDraft.state().pending && (!row.remote || !automaticDraft.state().stale)
            when (row) {
                is ConfigRow.Information -> Unit
                is ConfigRow.Action -> addRenderableWidget(
                    RestartableMarqueeButtonWidget.builder(row.name) { row.activate() }
                        .dimensions(layout.contentLeft, y, layout.contentWidth, FormScreenLayout.BUTTON_HEIGHT)
                        .build()
                        .also {
                            it.active = active
                            it.tooltip = Tooltip.create(ControlTooltip.description(row.description))
                        },
                )
                is ConfigRow.Toggle -> {
                    val enabled = row.enabled()
                    addRenderableWidget(
                        RestartableMarqueeButtonWidget.builder(booleanText(enabled)) { row.toggle() }
                            .pressesOnSecondaryClick()
                            .dimensions(layout.controlLeft, y, layout.controlWidth, FormScreenLayout.BUTTON_HEIGHT)
                            .build()
                            .also {
                                it.active = active
                                it.tooltip = Tooltip.create(
                                    ControlTooltip.options(
                                        description = row.description,
                                        options = listOf(
                                            ControlTooltip.Option(label = booleanText(true), current = enabled),
                                            ControlTooltip.Option(label = booleanText(false), current = !enabled),
                                        ),
                                        hints = if (active) ControlTooltip.toggleHints() else emptyList(),
                                    ),
                                )
                            },
                    )
                }
            }
        }
    }

    /**
     * 建立等寬排成一列的重設為預設、復原、套用、完成。
     *
     * @param layout 設定頁版面。
     */
    private fun addBottomButtons(layout: FormScreenLayout) {
        val footer = layout.footer(SettingsFooterButtons.COUNT)

        /**
         * 建立底部按鈕。
         *
         * @param index 按鈕在底部操作列中的順序。
         * @param key 按鈕文字翻譯鍵。
         * @param action 點擊時執行的動作。
         * @return 已加入畫面的按鈕。
         */
        fun button(index: Int, key: String, action: () -> Unit): Button = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(key)) { action() }
                .dimensions(footer.buttonX(index), layout.footerY, footer.buttonWidth(index), FormScreenLayout.BUTTON_HEIGHT)
                .build(),
        )
        resetButton = button(SettingsFooterButtons.RESET, MinecraftClientConfigScreenKeys.RESET_DEFAULTS) {
            draft = MahjongClientConfigState()
            automaticDraft.reset()
            saveFailed = false
            rebuild()
        }
        undoButton = button(SettingsFooterButtons.UNDO, MinecraftClientConfigScreenKeys.UNDO) { undoDraft() }
        applyButton = button(SettingsFooterButtons.APPLY, MinecraftClientConfigScreenKeys.APPLY) { applyDraft(closeAfterSave = false) }
        doneButton = button(SettingsFooterButtons.DONE, MinecraftClientConfigScreenKeys.DONE) {
            if (draftStale || (!hasChanges() && !preferenceService.hasPendingSync)) {
                onClose()
            } else {
                applyDraft(closeAfterSave = true)
            }
        }
    }

    /**
     * 先保存本機設定，再送出本局草稿並等待配對的伺服器確認。
     *
     * @param closeAfterSave 套用完成後是否關閉畫面。
     * @return 本機套用完成或已送出本局設定請求時為 true；不代表伺服器已接受請求。
     */
    private fun applyDraft(closeAfterSave: Boolean): Boolean {
        if (draftStale || automaticDraft.state().pending) return false
        if (draft != baseline || preferenceService.hasPendingSync) {
            when (preferenceService.saveDraft(draft, minecraft?.connection != null)) {
                is ClientAutoSortHandPreferenceUpdateResult.SaveFailed -> {
                    saveFailed = true
                    refreshButtons()
                    return false
                }
                is ClientAutoSortHandPreferenceUpdateResult.SyncFailed -> {
                    baseline = configStore.current
                    baselineRevision = configStore.revision
                    preferenceSyncFailed = true
                    refreshButtons()
                    return false
                }
                else -> Unit
            }
            baseline = configStore.current
            baselineRevision = configStore.revision
            preferenceSyncFailed = false
            saveFailed = false
        }
        val remote = automaticDraft.state()
        if (remote.stale) {
            automaticError = AutomaticControlError.STALE
            refreshButtons()
            return false
        }
        if (remote.dirty) {
            when (val submission = automaticCoordinator.submit(remote.enabledControlIds)) {
                is ClientAutomaticControlSubmitResult.Submitted -> {
                    check(automaticDraft.markSubmitted(submission.request, closeAfterSave))
                    automaticError = null
                    rebuild()
                    return true
                }
                is ClientAutomaticControlSubmitResult.Pending -> automaticError = AutomaticControlError.PENDING
                is ClientAutomaticControlSubmitResult.Unavailable -> automaticError = AutomaticControlError.UNAVAILABLE
                is ClientAutomaticControlSubmitResult.Unsupported -> automaticError = AutomaticControlError.REJECTED
                is ClientAutomaticControlSubmitResult.SendFailed -> automaticError = AutomaticControlError.SEND_FAILED
            }
            refreshButtons()
            return false
        }
        if (closeAfterSave) minecraft?.setScreen(parent) else rebuild()
        return true
    }

    /** 目前完整草稿，供 HUD editor 在不建立第二份設定來源的情況下承接。 */
    internal fun currentDraft(): MahjongClientConfigState = draft

    /**
     * 將 HUD 配置併入完整草稿並透過既有原子保存流程套用。
     *
     * @param layout 欲套用的 HUD 配置。
     * @return 本機保存與所需同步成功時為 true；失敗時仍保留修改後的草稿。
     */
    internal fun applyHudLayout(layout: MahjongHudLayoutConfig): Boolean {
        if (draftStale || automaticDraft.state().pending) return false
        draft = draft.copy(hudLayout = layout)
        return when (preferenceService.saveDraft(draft, minecraft?.connection != null)) {
            is ClientAutoSortHandPreferenceUpdateResult.SaveFailed -> {
                saveFailed = true
                false
            }
            is ClientAutoSortHandPreferenceUpdateResult.SyncFailed -> {
                baseline = configStore.current
                baselineRevision = configStore.revision
                preferenceSyncFailed = true
                false
            }
            else -> {
                baseline = configStore.current
                baselineRevision = configStore.revision
                saveFailed = false
                preferenceSyncFailed = false
                rebuild()
                true
            }
        }
    }

    /** 依目前草稿、預設值與 revision 更新底部按鈕狀態。 */
    private fun refreshButtons() {
        val remote = automaticDraft.state()
        resetButton?.active = !draftStale && !remote.pending && (draft != MahjongClientConfigState() || remote.enabledControlIds.isNotEmpty())
        applyButton?.active = !draftStale && !remote.pending && (hasChanges() || preferenceService.hasPendingSync)
        undoButton?.active = !remote.pending && (draftStale || hasChanges() || remote.stale)
        doneButton?.active = !remote.pending
        val changes = if (hasChanges()) Tooltip.create(differenceText()) else null
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
        automaticDraft.undo()
        automaticError = null
        preferenceSyncFailed = preferenceService.hasPendingSync
        saveFailed = false
        rebuild()
    }

    /** 兩份草稿中任一份尚未套用。 */
    private fun hasChanges(): Boolean = draft != baseline || automaticDraft.state().dirty

    /** 所有套用入口使用同一份本機與本局草稿差異。 */
    private fun differenceText(): Component = clientConfigDifferenceText(baseline, draft, automaticDraft.state(), displayResolver)

    /**
     * 繪製欄位名稱與說明列；放不下時截斷。
     *
     * @param context 畫面繪製環境。
     * @param layout 設定頁版面。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標下被截斷的文字全文；游標不在被截斷的文字上時為 null。
     */
    private fun renderFieldLabels(context: GuiGraphics, layout: FormScreenLayout, mouseX: Int, mouseY: Int): Component? {
        var hoveredLabel: Component? = null
        rows().drop(rowScroll.index).take(layout.visibleFieldCount).forEachIndexed { index, row ->
            val rowTop = layout.fieldRowTop(index)
            layout.fieldStripe(index).renderIfStriped(context, rowScroll.index + index)
            val hovered = when (row) {
                is ConfigRow.Action -> null
                is ConfigRow.Information -> ScreenRendering.label(
                    context = context,
                    textRenderer = font,
                    label = row.name,
                    x = layout.contentLeft,
                    y = rowTop + (FormScreenLayout.BUTTON_HEIGHT - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2,
                    maximumWidth = layout.contentWidth,
                    mouseX = mouseX,
                    mouseY = mouseY,
                    color = ScreenRendering.MUTED_TEXT_COLOR,
                )
                is ConfigRow.Toggle -> ScreenRendering.label(
                    context = context,
                    textRenderer = font,
                    label = row.name,
                    x = layout.contentLeft,
                    y = layout.fieldLabelY(rowTop),
                    maximumWidth = layout.labelWidth,
                    mouseX = mouseX,
                    mouseY = mouseY,
                )
            }
            hovered?.let { hoveredLabel = it }
        }
        return hoveredLabel
    }

    /**
     * 繪製草稿過期、保存失敗或等待伺服器確認的狀態。
     *
     * @param context 畫面繪製環境。
     * @param layout 設定頁版面。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     */
    private fun renderStatus(context: GuiGraphics, layout: FormScreenLayout, mouseX: Int, mouseY: Int) {
        val remote = automaticDraft.state()
        val error = ScreenRendering.ERROR_TEXT_COLOR
        val (status, color) = when {
            draftStale -> Component.translatable(MinecraftClientConfigScreenKeys.DRAFT_STALE) to error
            saveFailed -> Component.translatable(MinecraftClientConfigScreenKeys.SAVE_FAILED) to error
            preferenceSyncFailed -> Component.translatable(MinecraftMessageKeys.AUTOMATIC_CONTROL_PREFERENCE_SYNC_FAILED) to error
            remote.pending -> Component.translatable(pendingStatusKey(checkNotNull(remote.pendingRequestId))) to ScreenRendering.TEXT_COLOR
            automaticError == AutomaticControlError.UNCONFIRMED -> Component.translatable(AutomaticControlError.UNCONFIRMED.translationKey) to error
            remote.stale -> Component.translatable(MinecraftClientConfigScreenKeys.AUTOMATIC_STALE) to error
            automaticError != null -> Component.translatable(checkNotNull(automaticError).translationKey) to error
            else -> return
        }
        ScreenRendering.status(
            context = context,
            textRenderer = font,
            layout = layout,
            message = status,
            color = color,
            marquee = statusMarquee,
            mouseX = mouseX,
            mouseY = mouseY,
        )
    }

    /**
     * 等待確認時的狀態文字；等不到回覆而正在重新同步時改用重新同步文字。
     *
     * @param requestId 等待中的請求識別碼。
     * @return 狀態文字的翻譯鍵。
     */
    private fun pendingStatusKey(requestId: String): String = if (automaticCoordinator.isResyncing(requestId)) {
        MinecraftClientConfigScreenKeys.AUTOMATIC_RESYNCING
    } else {
        MinecraftClientConfigScreenKeys.AUTOMATIC_PENDING
    }

    /** 取得目前分類的宣告式欄位。 */
    private fun rows(): List<ConfigRow> = when (category) {
        Category.AUTOMATIC -> listOf(
            element = localToggle(
                nameKey = MinecraftClientConfigScreenKeys.AUTO_SORT_HAND,
                descriptionKey = MinecraftClientConfigScreenKeys.AUTO_SORT_HAND_DESCRIPTION,
                read = { it.autoSortHandEnabled },
                update = { config, enabled -> config.copy(autoSortHandEnabled = enabled) },
            ),
        ) + automaticRows()

        Category.HUD -> listOf(
            element = ConfigRow.Action(
                name = Component.translatable(MinecraftClientConfigScreenKeys.EDIT_HUD_LAYOUT),
                description = Component.translatable(MinecraftClientConfigScreenKeys.EDIT_HUD_LAYOUT_DESCRIPTION),
                activate = {
                    minecraft?.setScreen(
                        MahjongHudLayoutEditorScreen(
                            parent = this,
                            initialLayout = currentDraft().hudLayout,
                            automaticControlLabels = automaticControlPreviewLabels(),
                            previewRenderer = hudPreviewRenderer,
                        ),
                    )
                },
            ),
        ) + presentationRows(MahjongPresentationToggleSection.HUD)

        Category.GAME_PANELS -> presentationRows(MahjongPresentationToggleSection.GAME_PANELS)
        Category.VISUAL_FEEDBACK -> listOf(
            element = localToggle(
                nameKey = MinecraftClientConfigScreenKeys.TILE_LABELS,
                descriptionKey = MinecraftClientConfigScreenKeys.TILE_LABELS_DESCRIPTION,
                read = { it.tileLabelsEnabled },
                update = { config, enabled -> config.copy(tileLabelsEnabled = enabled) },
            ),
        ) + presentationRows(MahjongPresentationToggleSection.VISUAL_FEEDBACK)
    }

    /**
     * 建立保存在本機設定草稿中的開關列。
     *
     * @param nameKey 欄位名稱翻譯鍵。
     * @param descriptionKey 欄位說明翻譯鍵。
     * @param read 從草稿讀取開關狀態。
     * @param update 以新的開關狀態建立草稿。
     * @return 切換時更新本機設定草稿的開關列。
     */
    private fun localToggle(
        nameKey: String,
        descriptionKey: String,
        read: (MahjongClientConfigState) -> Boolean,
        update: (MahjongClientConfigState, Boolean) -> MahjongClientConfigState,
    ): ConfigRow = ConfigRow.Toggle(
        name = Component.translatable(nameKey),
        description = Component.translatable(descriptionKey),
        enabled = { read(draft) },
        toggle = {
            draft = update(draft, !read(draft))
            saveFailed = false
            rebuild()
        },
    )

    /**
     * 供 HUD 位置編輯器量測自動操作狀態預覽的項目名稱；本局有權威快照時用實際支援的項目，
     * 不在對局中則改用內建項目當樣本，讓玩家在任何時候都能先安排位置。
     */
    private fun automaticControlPreviewLabels(): List<Component> {
        val baseline = automaticDraft.state().baseline?.takeIf { it.supportedControlIds.isNotEmpty() }
        val ruleModuleId = baseline?.ruleModuleId
        val supported = baseline?.supportedControlIds ?: AUTOMATIC_CONTROL_PREVIEW_SAMPLE_IDS
        val sortId = BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND
        return listOf(displayResolver.resolve(ruleModuleId, sortId).label) +
            displayResolver.resolveAll(ruleModuleId, supported - sortId).map { it.label }
    }

    /** 依權威支援集合建立本局控制列，不憑規則名稱猜測可用功能。 */
    private fun automaticRows(): List<ConfigRow> {
        val snapshot = automaticDraft.state().baseline
        val explanation = when {
            snapshot == null -> MinecraftClientConfigScreenKeys.AUTOMATIC_UNAVAILABLE
            snapshot.supportedControlIds.isEmpty() -> MinecraftClientConfigScreenKeys.AUTOMATIC_EMPTY
            else -> MinecraftClientConfigScreenKeys.AUTOMATIC_ROUND_ONLY
        }
        val heading = ConfigRow.Information(Component.translatable(explanation))
        if (snapshot == null) return listOf(heading)
        return listOf(heading) + displayResolver.resolveAll(snapshot.ruleModuleId, snapshot.supportedControlIds).map { display ->
            ConfigRow.Toggle(
                name = display.label,
                description = display.description,
                enabled = { display.controlId in automaticDraft.state().enabledControlIds },
                toggle = {
                    val enabled = automaticDraft.state().enabledControlIds
                    val next = if (display.controlId in enabled) enabled - display.controlId else enabled + display.controlId
                    if (automaticDraft.replaceDraft(next)) {
                        automaticError = null
                        rebuild()
                    }
                },
                remote = true,
            )
        }
    }

    /**
     * 依宣告順序建立某個分頁的顯示項目開關列。
     *
     * @param section 要列出的分頁。
     * @return 這個分頁每個顯示項目一列的開關列。
     */
    private fun presentationRows(section: MahjongPresentationToggleSection): List<ConfigRow> = MahjongPresentationToggle.entries
        .filter { it.section == section }
        .map { toggle ->
            localToggle(
                nameKey = toggle.nameKey,
                descriptionKey = toggle.descriptionKey,
                read = { toggle.read(it.presentationVisibility) },
                update = { config, enabled -> config.copy(presentationVisibility = toggle.update(config.presentationVisibility, enabled)) },
            )
        }

    /**
     * 將 Boolean 轉換成本地化的開關狀態。
     *
     * @param value 是否啟用。
     * @return 啟用或停用的本地化文字。
     */
    private fun booleanText(value: Boolean): Component = Component.translatable(
        if (value) MinecraftClientConfigScreenKeys.ENABLED else MinecraftClientConfigScreenKeys.DISABLED,
    )

    /** 在目前輸入事件完成後安全重建 widgets，避免舊 widget 被重新設為 focus。 */
    private fun rebuild() {
        rebuildRequested = true
    }

    /**
     * 依目前視窗尺寸建立設定頁版面。
     *
     * @return 沒有頁面切換與規則列的設定頁版面。
     */
    private fun layout(): FormScreenLayout = FormScreenLayout(
        windowWidth = width,
        windowHeight = height,
        hasPageSwitch = false,
        hasRuleRow = false,
    )

    /**
     * 依目前捲動位置建立分類頁籤列版面。
     *
     * @param layout 設定頁版面。
     * @return 頁籤列版面。
     */
    private fun tabStrip(layout: FormScreenLayout): TabStripLayout = TabStripLayout(
        left = layout.contentLeft,
        width = layout.contentWidth,
        top = layout.tabsTop,
        tabCount = Category.entries.size,
        scroll = tabScroll,
    )

    /**
     * 設定分類。
     *
     * @property translationKey 分類名稱的翻譯鍵。
     */
    private enum class Category(val translationKey: String) {
        /** HUD 顯示。 */
        HUD(MinecraftClientConfigScreenKeys.CATEGORY_HUD),

        /** 世界空間面板。 */
        GAME_PANELS(MinecraftClientConfigScreenKeys.CATEGORY_GAME_PANELS),

        /** 世界視覺提示。 */
        VISUAL_FEEDBACK(MinecraftClientConfigScreenKeys.CATEGORY_VISUAL_FEEDBACK),

        /** 永久理牌偏好與本局自動操作。 */
        AUTOMATIC(MinecraftClientConfigScreenKeys.CATEGORY_AUTOMATIC),
    }

    /** 分類中的一列。 */
    private sealed interface ConfigRow {
        /** 這一列顯示的名稱或說明文字。 */
        val name: Component

        /** 本局權威狀態過期時是否不可操作。 */
        val remote: Boolean
            get() = false

        /**
         * 有開啟與關閉兩種狀態的設定。
         *
         * @property name 欄位名稱。
         * @property description 欄位說明；沒有說明時為 null。
         * @property enabled 讀取目前草稿中的開關狀態。
         * @property toggle 切換開關並更新草稿。
         * @property remote 本局權威狀態過期時是否不可操作。
         */
        data class Toggle(
            override val name: Component,
            val description: Component?,
            val enabled: () -> Boolean,
            val toggle: () -> Unit,
            override val remote: Boolean = false,
        ) : ConfigRow

        /**
         * 沒有對應值的動作，佔滿整列的一顆按鈕。
         *
         * @property name 按鈕文字。
         * @property description 按鈕說明。
         * @property activate 按下按鈕時執行的動作。
         */
        data class Action(
            override val name: Component,
            val description: Component,
            val activate: () -> Unit,
        ) : ConfigRow

        /**
         * 沒有控制項的說明列。
         *
         * @property name 說明文字。
         */
        data class Information(
            override val name: Component,
        ) : ConfigRow
    }

    private companion object {
        /** 不在對局中時，用來量測自動操作狀態預覽的內建項目樣本。 */
        val AUTOMATIC_CONTROL_PREVIEW_SAMPLE_IDS = setOf(
            BuiltInAutomaticControlIds.AUTO_WIN,
            BuiltInAutomaticControlIds.DECLINE_CALLS,
            BuiltInAutomaticControlIds.AUTO_TSUMOGIRI,
        )
    }
}

/**
 * 設定畫面上本局自動操作送出或確認失敗的狀態，各自帶有要顯示的訊息。
 *
 * @property translationKey 狀態訊息的翻譯鍵。
 */
private enum class AutomaticControlError(val translationKey: String) {
    /** 上一次送出仍在等待伺服器確認。 */
    PENDING(MinecraftMessageKeys.AUTOMATIC_CONTROL_PENDING),

    /** 目前沒有可以變更的本局。 */
    UNAVAILABLE(MinecraftMessageKeys.AUTOMATIC_CONTROL_UNAVAILABLE),

    /** 變更不被接受。 */
    REJECTED(MinecraftMessageKeys.AUTOMATIC_CONTROL_REJECTED),

    /** 變更沒有送出。 */
    SEND_FAILED(MinecraftMessageKeys.AUTOMATIC_CONTROL_SEND_FAILED),

    /** 草稿根據的狀態已過期。 */
    STALE(MinecraftClientConfigScreenKeys.AUTOMATIC_STALE),

    /** 伺服器確認收到，但變更沒有套用。 */
    NOT_APPLIED(MinecraftClientConfigScreenKeys.AUTOMATIC_NOT_APPLIED),

    /** 等不到伺服器確認。 */
    UNCONFIRMED(MinecraftClientConfigScreenKeys.AUTOMATIC_UNCONFIRMED),
}

/** 將伺服器確認結果轉成畫面上的失敗狀態；接受時沒有失敗狀態。 */
private fun ClientAutomaticControlResultKind.toAutomaticControlError(): AutomaticControlError? = when (this) {
    ClientAutomaticControlResultKind.ACCEPTED -> null
    ClientAutomaticControlResultKind.REJECTED -> AutomaticControlError.REJECTED
    ClientAutomaticControlResultKind.STALE -> AutomaticControlError.STALE
    ClientAutomaticControlResultKind.UNAVAILABLE -> AutomaticControlError.UNAVAILABLE
    ClientAutomaticControlResultKind.NOT_APPLIED -> AutomaticControlError.NOT_APPLIED
    ClientAutomaticControlResultKind.UNCONFIRMED -> AutomaticControlError.UNCONFIRMED
}
