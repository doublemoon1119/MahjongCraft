package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlDisplayResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferenceService
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutoSortHandPreferenceUpdateResult
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlDraftSession
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlSubmitResult
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlUpdateCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.SettingsFooterLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.UnsavedChangesConfirmationScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/** 原生 MahjongCraft client 設定畫面；分開編輯本機設定與本局自動操作草稿，不接觸房間規則。 */
class MahjongClientConfigScreen(
    private val parent: Screen?,
    private val configStore: MahjongClientConfigStore,
    private val automaticCoordinator: ClientAutomaticControlUpdateCoordinator,
    private val displayResolver: AutomaticControlDisplayResolver,
    private val preferenceService: ClientAutoSortHandPreferenceService,
) : Screen(Text.translatable(MinecraftClientConfigScreenKeys.TITLE)) {
    /** 伺服器權威狀態之外的畫面專用本局草稿。 */
    private val automaticDraft = ClientAutomaticControlDraftSession(automaticCoordinator.snapshot())

    /** 最近一次送出／確認失敗的本局控制狀態。 */
    private var automaticError: String? = null

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

    /** 下一個 client tick 是否需要安全重建 widgets。 */
    private var rebuildRequested = false

    /** 套用按鈕，供草稿狀態即時更新。 */
    private var applyButton: ButtonWidget? = null

    /** 復原按鈕，供草稿狀態即時更新。 */
    private var undoButton: ButtonWidget? = null

    /** 重設按鈕，供草稿狀態即時更新。 */
    private var resetButton: ButtonWidget? = null

    /** 完成按鈕，等待 ACK 時禁止離開。 */
    private var doneButton: ButtonWidget? = null

    override fun init() {
        applyButton = null
        undoButton = null
        resetButton = null
        val bounds = panelBounds()
        if (bounds.compact) {
            val categoryWidth = (bounds.width - PANEL_PADDING * 2 - BOTTOM_BUTTON_GAP) / 2
            Category.entries.forEachIndexed { index, entry ->
                addCategoryButton(
                    bounds.left + PANEL_PADDING + index % 2 * (categoryWidth + BOTTOM_BUTTON_GAP),
                    bounds.contentTop + index / 2 * ClientConfigCategoryLayout.CATEGORY_ROW_HEIGHT,
                    categoryWidth,
                    entry,
                )
            }
        } else {
            Category.entries.forEachIndexed { index, entry ->
                addCategoryButton(
                    bounds.left + PANEL_PADDING,
                    bounds.contentTop + index * CATEGORY_BUTTON_GAP,
                    bounds.sidebarButtonWidth,
                    entry,
                )
            }
        }
        addFieldButtons(bounds)
        addBottomButtons(bounds)
        refreshButtons()
    }

    /** 設定畫面不暫停整合伺服器或單人遊戲。 */
    override fun shouldPause(): Boolean = false

    /** Esc 在無變更時返回；有未套用草稿時顯示明確的三選項確認畫面。 */
    override fun close() {
        if (automaticDraft.state().pending) return
        if (!hasChanges()) {
            client?.setScreen(parent)
        } else {
            client?.setScreen(
                UnsavedChangesConfirmationScreen(
                    this,
                    {
                        client?.setScreen(this)
                        applyDraft(closeAfterSave = true)
                    },
                    { client?.setScreen(parent) },
                    differenceText(),
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
        val oldRemote = automaticDraft.state()
        automaticDraft.applySnapshot(automaticCoordinator.snapshot())
        if (oldRemote != automaticDraft.state()) rebuild()
        automaticDraft.state().pendingRequestId?.let { requestId ->
            automaticCoordinator.takeCompletion(requestId)?.let { completion ->
                val result = automaticDraft.applyCompletion(completion)
                automaticError = if (result?.accepted == true) null else completion.result.result.name
                if (result?.closeAfterAcceptance == true) {
                    client?.setScreen(parent)
                    return
                }
                rebuild()
            }
        }
        refreshButtons()
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
        val hoveredLabel = renderFieldLabels(context, bounds, mouseX, mouseY)
        renderStatus(context, bounds)
        renderScrollbar(context, bounds)
        super.render(context, mouseX, mouseY, delta)
        hoveredLabel?.let { context.drawTooltip(textRenderer, it, mouseX, mouseY) }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (isInsideFields(mouseX, mouseY, panelBounds()) && rowScroll.scrollBy(amount, maximumScroll())) {
            rebuild()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && isOverScrollbar(mouseX, mouseY, panelBounds())) {
            if (rowScroll.beginDrag(mouseY, scrollbarLayout(panelBounds()))) rebuild()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (rowScroll.dragging && button == 0) {
            if (rowScroll.dragTo(mouseY, scrollbarLayout(panelBounds()))) rebuild()
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        rowScroll.endDrag()
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /** 建立分類切換按鈕。 */
    private fun addCategoryButton(x: Int, y: Int, buttonWidth: Int, target: Category) {
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(target.translationKey)) {
                category = target
                rowScroll.reset()
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
        rowScroll.clamp(rows.size - visible)
        rows.drop(rowScroll.index).take(visible).forEachIndexed { index, row ->
            if (row.information) return@forEachIndexed
            val y = rowsTop(bounds) + index * FIELD_ROW_HEIGHT
            val message = if (bounds.compact) {
                row.nameText().copy().append(": ").append(row.valueText(draft))
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
                it.active = (row.update != null || row.onActivate != null) &&
                    !draftStale &&
                    !automaticDraft.state().pending &&
                    (!row.remote || !automaticDraft.state().stale)
                it.tooltip = row.descriptionText()?.let(Tooltip::of) ?: row.nameOverride?.let(Tooltip::of)
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
                automaticDraft.reset()
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
        doneButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftClientConfigScreenKeys.DONE)) {
                if (draftStale || (!hasChanges() && !preferenceService.hasPendingSync)) {
                    close()
                } else {
                    applyDraft(closeAfterSave = true)
                }
            }.dimensions(footer.doneX, y, footer.actionWidth, BUTTON_HEIGHT).build(),
        )
    }

    /** 先保存本機設定，再送出本局草稿並等待配對的伺服器確認。 */
    private fun applyDraft(closeAfterSave: Boolean): Boolean {
        if (draftStale || automaticDraft.state().pending) return false
        if (draft != baseline || preferenceService.hasPendingSync) {
            when (preferenceService.saveDraft(draft, client?.networkHandler != null)) {
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
            automaticError = AutomaticControlUpdateResultKindDto.STALE.name
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
                is ClientAutomaticControlSubmitResult.Pending -> automaticError = "PENDING"
                is ClientAutomaticControlSubmitResult.Unavailable -> automaticError = "UNAVAILABLE"
                is ClientAutomaticControlSubmitResult.Unsupported -> automaticError = "UNSUPPORTED"
                is ClientAutomaticControlSubmitResult.SendFailed -> automaticError = "SEND_FAILED"
            }
            refreshButtons()
            return false
        }
        if (closeAfterSave) client?.setScreen(parent) else rebuild()
        return true
    }

    /** 目前完整草稿，供 HUD editor 在不建立第二份設定來源的情況下承接。 */
    internal fun currentDraft(): MahjongClientConfigState = draft

    /** 將 HUD 配置併入完整草稿並透過既有原子保存流程套用。 */
    internal fun applyHudLayout(layout: MahjongHudLayoutConfig): Boolean {
        if (draftStale || automaticDraft.state().pending) return false
        draft = draft.copy(hudLayout = layout)
        return when (preferenceService.saveDraft(draft, client?.networkHandler != null)) {
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
        val changes = if (hasChanges()) Tooltip.of(differenceText()) else null
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
    private fun differenceText(): Text = clientConfigDifferenceText(baseline, draft, automaticDraft.state(), displayResolver)

    /**
     * 繪製欄位名稱，控制項寬度固定且名稱依實際像素寬度安全截斷；名稱被截斷且滑鼠懸停時回傳完整
     * 名稱供呼叫端繪製 tooltip。
     */
    private fun renderFieldLabels(context: DrawContext, bounds: PanelBounds, mouseX: Int, mouseY: Int): Text? {
        var hoveredLabel: Text? = null
        val rows = rows()
        rows.drop(rowScroll.index).take(visibleRowCount(bounds)).forEachIndexed { index, row ->
            if (bounds.compact && !row.information) return@forEachIndexed
            val label = row.nameText()
            val y = rowsTop(bounds) + index * FIELD_ROW_HEIGHT + VANILLA_TEXT_OFFSET_Y
            val left = if (row.information) bounds.contentLeft else bounds.left + SIDEBAR_WIDTH + PANEL_PADDING
            val available = if (row.information) bounds.right - PANEL_PADDING - left else bounds.controlLeft - PANEL_PADDING - left
            val fittedLabel = fitText(label, available)
            context.drawTextWithShadow(textRenderer, fittedLabel, left, y, TEXT_COLOR)
            if (
                fittedLabel.string != label.string &&
                mouseX in left until left + available &&
                mouseY in y until y + textRenderer.fontHeight
            ) {
                hoveredLabel = label
            }
        }
        return hoveredLabel
    }

    /** 繪製草稿過期或保存失敗狀態。 */
    private fun renderStatus(context: DrawContext, bounds: PanelBounds) {
        val status = when {
            draftStale -> Text.translatable(MinecraftClientConfigScreenKeys.DRAFT_STALE).formatted(Formatting.RED)
            saveFailed -> Text.translatable(MinecraftClientConfigScreenKeys.SAVE_FAILED).formatted(Formatting.RED)
            preferenceSyncFailed -> Text.translatable("mahjongcraft.message.automatic_control_preference_sync_failed").formatted(Formatting.RED)
            automaticDraft.state().pending -> Text.translatable(MinecraftClientConfigScreenKeys.AUTOMATIC_PENDING)
            automaticDraft.state().stale -> Text.translatable(MinecraftClientConfigScreenKeys.AUTOMATIC_STALE).formatted(Formatting.RED)
            automaticError != null -> Text.translatable(automaticErrorKey(checkNotNull(automaticError))).formatted(Formatting.RED)
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

    /** 將本地提交錯誤與 ACK 結果映射到既有玩家訊息。 */
    private fun automaticErrorKey(error: String): String = when (error) {
        "PENDING" -> "mahjongcraft.message.automatic_control_pending"
        "UNAVAILABLE" -> "mahjongcraft.message.automatic_control_unavailable"
        "UNSUPPORTED" -> "mahjongcraft.message.automatic_control_rejected"
        "SEND_FAILED" -> "mahjongcraft.message.automatic_control_send_failed"
        "STALE" -> MinecraftClientConfigScreenKeys.AUTOMATIC_STALE
        else -> "mahjongcraft.message.automatic_control_rejected"
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

    /** 建立目前分類的 scrollbar 幾何。 */
    private fun scrollbarLayout(bounds: PanelBounds): ScrollbarLayout = ScrollbarLayout(
        trackTop = rowsTop(bounds),
        trackBottom = bounds.bottom - BOTTOM_AREA_HEIGHT,
        itemCount = rows().size,
        visibleItemCount = visibleRowCount(bounds),
        scrollIndex = rowScroll.index,
        minimumThumbHeight = MIN_SCROLLBAR_THUMB_HEIGHT,
    )

    /** 判斷游標是否位於欄位內容區。 */
    private fun isInsideFields(mouseX: Double, mouseY: Double, bounds: PanelBounds): Boolean = mouseX >= bounds.contentLeft &&
        mouseX < bounds.right &&
        mouseY >= rowsTop(bounds) &&
        mouseY < bounds.bottom - BOTTOM_AREA_HEIGHT

    /** 判斷游標是否位於有效 scrollbar。 */
    private fun isOverScrollbar(mouseX: Double, mouseY: Double, bounds: PanelBounds): Boolean = maximumScroll() > 0 &&
        mouseX >= bounds.right - SCROLLBAR_MARGIN &&
        mouseX < bounds.right - SCROLLBAR_MARGIN + SCROLLBAR_WIDTH &&
        mouseY >= rowsTop(bounds) &&
        mouseY < bounds.bottom - BOTTOM_AREA_HEIGHT

    /** 取得目前分類的宣告式欄位。 */
    private fun rows(): List<ConfigRow> = when (category) {
        Category.AUTOMATIC -> listOf(
            ConfigRow(
                MinecraftClientConfigScreenKeys.AUTO_SORT_HAND,
                MinecraftClientConfigScreenKeys.AUTO_SORT_HAND_DESCRIPTION,
                { booleanText(it.autoSortHandEnabled) },
                { it.copy(autoSortHandEnabled = !it.autoSortHandEnabled) },
            ),
        ) + automaticRows()

        Category.HUD -> listOf(
            ConfigRow(
                MinecraftClientConfigScreenKeys.EDIT_HUD_LAYOUT,
                MinecraftClientConfigScreenKeys.EDIT_HUD_LAYOUT_DESCRIPTION,
                { Text.translatable(MinecraftClientConfigScreenKeys.EDIT_HUD_LAYOUT) },
                onActivate = {
                    client?.setScreen(MahjongHudLayoutEditorScreen(this, currentDraft().hudLayout))
                },
            ),
            presentationRow("compact_prompt", { it.compactPromptEnabled }) { state, enabled -> state.copy(compactPromptEnabled = enabled) },
            presentationRow("discard_analysis", { it.discardAnalysisEnabled }) { state, enabled -> state.copy(discardAnalysisEnabled = enabled) },
        )

        Category.GAME_PANELS -> presentationRows(
            "round_info",
            "player_info",
            "lobby_info",
            "dice_result",
            "win_settlement",
            "draw_settlement",
            "match_settlement",
        )
        Category.VISUAL_FEEDBACK -> listOf(
            ConfigRow(
                MinecraftClientConfigScreenKeys.TILE_LABELS,
                MinecraftClientConfigScreenKeys.TILE_LABELS_DESCRIPTION,
                { booleanText(it.tileLabelsEnabled) },
                { it.copy(tileLabelsEnabled = !it.tileLabelsEnabled) },
            ),
        ) + presentationRows("matching_tile_highlight", "discard_popup", "meld_popup")
    }

    /** 依權威支援集合建立本局控制列，不憑規則名稱猜測可用功能。 */
    private fun automaticRows(): List<ConfigRow> {
        val remote = automaticDraft.state()
        val snapshot = remote.baseline
        val explanation = when {
            snapshot == null -> MinecraftClientConfigScreenKeys.AUTOMATIC_UNAVAILABLE
            snapshot.supportedControlIds.isEmpty() -> MinecraftClientConfigScreenKeys.AUTOMATIC_EMPTY
            else -> MinecraftClientConfigScreenKeys.AUTOMATIC_ROUND_ONLY
        }
        val heading = ConfigRow(explanation, explanation, { Text.empty() }, information = true)
        if (snapshot == null) return listOf(heading)
        return listOf(heading) + displayResolver.resolveAll(snapshot.supportedControlIds).map { display ->
            ConfigRow(
                nameKey = display.controlId,
                descriptionKey = display.controlId,
                valueText = { booleanText(display.controlId in automaticDraft.state().enabledControlIds) },
                onActivate = {
                    val enabled = automaticDraft.state().enabledControlIds
                    val next = if (display.controlId in enabled) enabled - display.controlId else enabled + display.controlId
                    if (automaticDraft.replaceDraft(next)) {
                        automaticError = null
                        rebuild()
                    }
                },
                nameOverride = display.label,
                descriptionOverride = display.description?.let { display.label.copy().append("\n").append(it) } ?: display.label,
                remote = true,
            )
        }
    }

    /** 建立 HUD、遊戲面板與視覺效果的個別開關列。 */
    private fun presentationRows(vararg includedIds: String): List<ConfigRow> = listOf(
        presentationRow("round_info", { it.roundInfoEnabled }) { state, enabled -> state.copy(roundInfoEnabled = enabled) },
        presentationRow("player_info", { it.playerInfoEnabled }) { state, enabled -> state.copy(playerInfoEnabled = enabled) },
        presentationRow("lobby_info", { it.lobbyInfoEnabled }) { state, enabled -> state.copy(lobbyInfoEnabled = enabled) },
        presentationRow("dice_result", { it.diceResultEnabled }) { state, enabled -> state.copy(diceResultEnabled = enabled) },
        presentationRow("compact_prompt", { it.compactPromptEnabled }) { state, enabled -> state.copy(compactPromptEnabled = enabled) },
        presentationRow("discard_analysis", { it.discardAnalysisEnabled }) { state, enabled -> state.copy(discardAnalysisEnabled = enabled) },
        presentationRow("win_settlement", { it.winSettlementEnabled }) { state, enabled -> state.copy(winSettlementEnabled = enabled) },
        presentationRow("draw_settlement", { it.drawSettlementEnabled }) { state, enabled -> state.copy(drawSettlementEnabled = enabled) },
        presentationRow("match_settlement", { it.matchSettlementEnabled }) { state, enabled -> state.copy(matchSettlementEnabled = enabled) },
        presentationRow("matching_tile_highlight", { it.matchingTileHighlightEnabled }) { state, enabled -> state.copy(matchingTileHighlightEnabled = enabled) },
        presentationRow("discard_popup", { it.discardPopupEnabled }) { state, enabled -> state.copy(discardPopupEnabled = enabled) },
        presentationRow("meld_popup", { it.meldPopupEnabled }) { state, enabled -> state.copy(meldPopupEnabled = enabled) },
    ).filter { it.id in includedIds }

    /** 建立一列 [MahjongPresentationVisibilityConfig] Boolean 開關。 */
    private fun presentationRow(
        id: String,
        read: (MahjongPresentationVisibilityConfig) -> Boolean,
        update: (MahjongPresentationVisibilityConfig, Boolean) -> MahjongPresentationVisibilityConfig,
    ): ConfigRow = ConfigRow(
        MinecraftClientConfigScreenKeys.presentationName(id),
        MinecraftClientConfigScreenKeys.presentationDescription(id),
        { booleanText(read(it.presentationVisibility)) },
        { config ->
            val visibility = config.presentationVisibility
            config.copy(presentationVisibility = update(visibility, !read(visibility)))
        },
        id = id,
    )

    /** 將 Boolean 轉換成本地化的開關狀態。 */
    private fun booleanText(value: Boolean): Text = Text.translatable(
        if (value) MinecraftClientConfigScreenKeys.ENABLED else MinecraftClientConfigScreenKeys.DISABLED,
    )

    /** 依實際像素寬度截斷過寬文字並補省略號。 */
    private fun fitText(text: Text, maximumWidth: Int): Text {
        if (maximumWidth <= 0) return Text.empty()
        if (textRenderer.getWidth(text) <= maximumWidth) return text
        val raw = text.string
        val suffix = "..."
        var end = raw.length
        while (end > 0 && textRenderer.getWidth(raw.substring(0, end) + suffix) > maximumWidth) end--
        return Text.literal(raw.substring(0, end) + suffix)
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
    private fun visibleRowCount(bounds: PanelBounds): Int = ((bounds.bottom - BOTTOM_AREA_HEIGHT - rowsTop(bounds)) / FIELD_ROW_HEIGHT).coerceAtLeast(1)

    /** 目前分類可捲動欄位的起始位置。 */
    private fun rowsTop(bounds: PanelBounds): Int = bounds.fieldsTop

    /** 目前分類最大的捲動列數。 */
    private fun maximumScroll(): Int = (rows().size - visibleRowCount(panelBounds())).coerceAtLeast(0)

    /** 設定分類。 */
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
        /** 供呈現分類過濾使用的穩定識別字。 */
        val id: String = nameKey,
        /** 動態 registry 名稱。 */
        val nameOverride: Text? = null,
        /** 動態 registry 說明。 */
        val descriptionOverride: Text? = null,
        /** 說明列沒有可編輯控制項。 */
        val information: Boolean = false,
        /** 本局權威狀態過期時不可切換。 */
        val remote: Boolean = false,
    ) {
        fun nameText(): Text = nameOverride ?: Text.translatable(nameKey)

        fun descriptionText(): Text? = descriptionOverride ?: if (information) null else Text.translatable(descriptionKey)
    }

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
            get() = if (compact) ClientConfigCategoryLayout.compactFieldsTop(top, Category.entries.size) else top + FIELDS_OFFSET_Y

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
        const val CONTENT_OFFSET_Y = ClientConfigCategoryLayout.CATEGORY_TOP_OFFSET

        /** 欄位相對面板上緣的 Y 位移。 */
        const val FIELDS_OFFSET_Y = 50

        /** 單欄版面分類按鈕結束後的欄位間距。 */
        /** 低於此面板寬度時改用單欄版面。 */
        const val TWO_COLUMN_MIN_WIDTH = 400

        /** 欄位列高。 */
        const val FIELD_ROW_HEIGHT = 30

        /** 原版按鈕高度。 */
        const val BUTTON_HEIGHT = 20

        /** 分類按鈕垂直間距。 */
        const val CATEGORY_BUTTON_GAP = ClientConfigCategoryLayout.CATEGORY_ROW_HEIGHT

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
