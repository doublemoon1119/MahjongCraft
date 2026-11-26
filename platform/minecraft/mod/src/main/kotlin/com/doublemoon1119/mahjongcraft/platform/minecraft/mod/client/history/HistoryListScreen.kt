package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortFieldDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.CycleButtonInput
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOption
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.DropdownOverlay
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.MahjongScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelIcon
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RowStripe
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

/**
 * 歷史對局分頁列表。查詢生命週期由共用瀏覽 session 管理。
 *
 * @property session 此畫面所屬的共用瀏覽 session。
 */
internal class HistoryListScreen(
    private val session: HistoryBrowseSession,
) : MahjongScreen(Component.translatable(MinecraftHistoryScreenKeys.TITLE)) {
    /** 目前版面幾何。 */
    private var layout = HistoryScreenLayout.measure(width = 0, height = 0)

    /** 排序欄位選擇器的展開清單。 */
    private val dropdown = DropdownOverlay()

    /** 目前列表捲動位置。 */
    private val scroll: Double get() = session.controller.state.value.list.scrollOffset

    /** 上一頁按鈕。 */
    private var previousButton: Button? = null

    /** 下一頁按鈕。 */
    private var nextButton: Button? = null

    /** 重新查詢按鈕。 */
    private var refreshButton: Button? = null

    /** 查詢失敗時的重試按鈕。 */
    private var retryButton: Button? = null

    /** 紀錄範圍按鈕。 */
    private var scopeButton: Button? = null

    /** 排序欄位選擇器。 */
    private var sortButton: Button? = null

    /** 排序方向按鈕。 */
    private var directionButton: RestartableMarqueeButtonWidget? = null

    /** 已套用條件的篩選入口。 */
    private var filterButton: Button? = null

    /** 是否正在拖曳捲軸。 */
    private var scrollDragging = false

    /** 拖曳時游標在捲軸滑塊內的抓取偏移。 */
    private var scrollGrabOffset = 0.0

    /** 查詢載入期間仍可顯示的上一批資料。 */
    private var cachedEntries: List<HistoryMatchSummaryDto> = emptyList()

    /** 本幀滑過的卡片提示，待內容區 scissor 關閉後繪製。 */
    private var hoveredEntry: List<Component>? = null

    /** 暫存卡片所屬條件，避免切換範圍或空結果沿用舊資料。 */
    private var cachedQuery: HistoryBrowseQuery? = null

    override fun init() {
        dropdown.close()
        layout = HistoryScreenLayout.measure(
            width = width,
            height = height,
            showLabelWidth = font.width(showLabel()),
            sortLabelWidth = font.width(sortLabel()),
        )
        scrollDragging = false
        clearWidgets()
        filterButton = button(Component.translatable(MinecraftHistoryScreenKeys.FILTERS_BUTTON), layout.filters) { session.openFilters() }
        scopeButton = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(scopeText()) { cycleScope() }
                .pressesOnSecondaryClick()
                .dimensions(layout.scope.x, layout.scope.y, layout.scope.width, layout.scope.height)
                .build(),
        )
        lateinit var sortSelector: RestartableMarqueeButtonWidget
        sortSelector = button(sortText(), layout.sort) { toggleSortList(sortSelector) }
        sortSelector.selectorExpanded = { dropdown.isOpenFor(SORT_SELECTOR_KEY) }
        sortButton = sortSelector
        directionButton = button(directionText(), layout.direction) { toggleDirection() }.also { it.icon = directionIcon() }
        refreshButton = button(Component.translatable(MinecraftHistoryScreenKeys.REFRESH), layout.refresh) { session.controller.refresh() }
        retryButton = button(Component.translatable(MinecraftHistoryScreenKeys.RETRY), layout.refresh) {
            if (session.controller.state.value.list.status is HistoryBrowseStatus.Failed) {
                session.controller.retry()
            } else if (session.archiveStatusController.view.value is HistoryArchiveStatusView.Failed) {
                session.retryArchiveStatus()
            }
        }
        previousButton = button(Component.translatable(MinecraftHistoryScreenKeys.PREVIOUS), layout.footerSlot(0)) {
            if (HistoryPaging.jumpsToEnd()) session.controller.firstPage() else session.controller.previousPage()
        }
        nextButton = button(Component.translatable(MinecraftHistoryScreenKeys.NEXT), layout.footerSlot(2)) {
            if (HistoryPaging.jumpsToEnd()) session.controller.lastPage() else session.controller.nextPage()
        }
        val leaveLabel = if (session.returnsToScreen) MinecraftHistoryScreenKeys.BACK else MinecraftHistoryScreenKeys.CLOSE
        button(Component.translatable(leaveLabel), layout.footerSlot(HistoryPageFrame.LAST_SLOT)) { onClose() }
    }

    /**
     * 加入沿用跑馬燈行為的按鈕。
     *
     * @param label 按鈕文字。
     * @param bounds 按鈕範圍。
     * @param action 按下後的行為。
     * @return 已加入畫面的按鈕。
     */
    private fun button(label: Component, bounds: HistoryScreenLayout.Bounds, action: () -> Unit): RestartableMarqueeButtonWidget = addRenderableWidget(
        RestartableMarqueeButtonWidget.builder(label) { action() }
            .dimensions(bounds.x, bounds.y, bounds.width, bounds.height)
            .build(),
    )

    /** 紀錄範圍按鈕左側的標籤。 */
    private fun showLabel(): Component = Component.translatable(MinecraftHistoryScreenKeys.SHOW_LABEL)

    /** 排序欄位選擇器左側的標籤。 */
    private fun sortLabel(): Component = Component.translatable(MinecraftHistoryScreenKeys.SORT_TITLE)

    /**
     * 展開或收合排序欄位清單。
     *
     * @param anchor 排序欄位選擇器。
     */
    private fun toggleSortList(anchor: RestartableMarqueeButtonWidget) {
        val fields = availableSortFields()
        dropdown.toggle(
            key = SORT_SELECTOR_KEY,
            anchor = anchor,
            options = fields.map { DropdownOption(label = Component.translatable(it.translationKey)) },
            selectedIndex = fields.indexOf(session.controller.state.value.query.sortField).takeIf { it >= 0 },
        ) { index -> selectSort(fields[index]) }
    }

    override fun resize(client: Minecraft, width: Int, height: Int) {
        super.resize(client, width, height)
        session.controller.rememberListPosition(scroll)
    }

    override fun mouseWheelScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (dropdown.mouseScrolled(font, height, amount)) return true
        if (mouseY < layout.contentTop || mouseY > layout.contentBottom) return super.mouseWheelScrolled(mouseX, mouseY, amount)
        session.controller.rememberListPosition(layout.clampScroll(scroll - amount * 18.0, contentHeight(displayEntries())))
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (dropdown.mouseClicked(font, height, mouseX, mouseY, button)) return true
        val scrollbar = layout.scrollbar(contentHeight(displayEntries()), scroll)
        if (button == 0 && scrollbar.maximumScroll > 0 && layout.isOverScrollbar(mouseX, mouseY)) {
            scrollDragging = true
            scrollGrabOffset = scrollbar.grabOffset(mouseY)
            updateDragScroll(mouseY)
            return true
        }
        val list = session.controller.state.value.list
        if (button == 0 && !scrollDragging && list.status == HistoryBrowseStatus.Ready) {
            val index = layout.cardIndexAt(mouseX, mouseY, scroll, list.entries.map(::entryHeight))
            if (index != null) {
                val matchId = list.entries[index].matchId
                session.controller.rememberListPosition(scroll, matchId)
                if (session.openSummary(matchId)) return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (dropdown.isOpen) return true
        if (scrollDragging && button == 0) {
            updateDragScroll(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = scrollDragging && button == 0
        scrollDragging = false
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (dropdown.keyPressed(font, height, keyCode)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /**
     * 依拖曳位置更新列表捲動偏移。
     *
     * @param mouseY 游標垂直座標。
     */
    private fun updateDragScroll(mouseY: Double) {
        val scrollbar = layout.scrollbar(contentHeight(displayEntries()), scroll)
        session.controller.rememberListPosition(scrollbar.scrollIndexFor(mouseY, scrollGrabOffset).toDouble())
    }

    /** 取得目前顯示的資料，讓繪製、滾輪與拖曳使用相同的內容高度。 */
    private fun displayEntries(): List<HistoryMatchSummaryDto> {
        val state = session.controller.state.value
        return if (state.list.status == HistoryBrowseStatus.Loading && state.list.entries.isEmpty() && cachedQuery == state.query) {
            cachedEntries
        } else {
            state.list.entries
        }
    }

    /**
     * 計算清單高度。
     *
     * @param entries 目前顯示的對局摘要。
     * @return 捲動內容的總像素高度。
     */
    private fun contentHeight(entries: List<HistoryMatchSummaryDto>): Int {
        measureCards(entries)
        return cardMeasurements.values.sumOf(CardMeasurement::height)
    }

    /** 取得目前紀錄範圍的按鈕文字。 */
    private fun scopeText(): Component = Component.translatable(session.controller.state.value.query.scope.translationKey)

    /** 取得目前排序欄位的按鈕文字。 */
    private fun sortText(): Component = Component.translatable(session.controller.state.value.query.sortField.translationKey)

    /** 取得目前排序方向的名稱，供旁白使用。 */
    private fun directionText(): Component = Component.translatable(session.controller.state.value.query.sortDirection.translationKey)

    /** 取得目前排序方向的箭頭圖示。 */
    private fun directionIcon(): PixelIcon = if (session.controller.state.value.query.sortDirection == HistorySortDirectionDto.ASC) {
        PixelIcon.SORT_ASCENDING
    } else {
        PixelIcon.SORT_DESCENDING
    }

    /** 建立紀錄範圍按鈕的選項 tooltip。 */
    private fun scopeTooltip(): Component = ControlTooltip.options(
        description = Component.translatable(MinecraftHistoryScreenKeys.SCOPE_TITLE),
        labels = availableScopes().map { Component.translatable(it.translationKey) },
        currentIndex = availableScopes().indexOf(session.controller.state.value.query.scope),
        hints = CycleButtonInput.hintTexts(optionCount = availableScopes().size, active = scopeButton?.active == true),
    )

    /** 只提供最近權威回應允許的查詢範圍。 */
    private fun availableScopes(): List<HistoryQueryScopeDto> = buildList {
        val state = session.controller.state.value
        add(HistoryQueryScopeDto.OWN)
        if (state.allowAll) add(HistoryQueryScopeDto.ALL)
        if (state.allowStressTest) add(HistoryQueryScopeDto.STRESS_TEST)
    }

    /** 全部紀錄與壓力測試資料沒有個人名次或個人分數排序。 */
    private fun availableSortFields(): List<HistorySortFieldDto> = if (session.controller.state.value.query.scope.coversAllMatches) listOf(HistorySortFieldDto.ENDED_AT, HistorySortFieldDto.DURATION) else HistorySortFieldDto.entries

    /** 建立排序欄位選擇器的選項 tooltip。 */
    private fun sortTooltip(): Component = ControlTooltip.options(
        description = Component.translatable(MinecraftHistoryScreenKeys.SORT_TITLE),
        labels = availableSortFields().map { Component.translatable(it.translationKey) },
        currentIndex = availableSortFields().indexOf(session.controller.state.value.query.sortField),
    )

    /** 建立排序方向按鈕的選項 tooltip。 */
    private fun directionTooltip(): Component = ControlTooltip.options(
        description = Component.translatable(MinecraftHistoryScreenKeys.DIRECTION_TITLE),
        labels = HistorySortDirectionDto.entries.map { Component.translatable(it.translationKey) },
        currentIndex = session.controller.state.value.query.sortDirection.ordinal,
        hints = CycleButtonInput.hintTexts(optionCount = HistorySortDirectionDto.entries.size, active = directionButton?.active == true),
    )

    /** 僅列出已套用條件，不將尚未通過驗證的草稿混入。 */
    private fun appliedFiltersTooltip(): Component {
        val filters = session.controller.state.value.query.filters
        val lines = mutableListOf<Component>()

        /**
         * 將欄位名稱與已套用值加入提示。
         *
         * @param key 欄位名稱的翻譯鍵。
         * @param value 已套用值的呈現文字。
         */
        fun add(key: String, value: Component) {
            lines += Component.translatable(key).copy().append(": ").append(value)
        }
        filters.ruleId?.let { add(MinecraftHistoryScreenKeys.FILTER_RULE, HistoryScreenText.rule(it, session.ruleNames)) }
        filters.playerName?.let { add(MinecraftHistoryScreenKeys.FILTER_PLAYER_NAME, Component.literal(it)) }
        filters.matchId?.let { add(MinecraftHistoryScreenKeys.FILTER_MATCH_ID, Component.literal(it)) }
        filters.endedAtFromEpochMillis?.let { add(MinecraftHistoryScreenKeys.FILTER_FROM, Component.literal(HistoryScreenText.endedAt(it))) }
        filters.endedAtBeforeEpochMillis?.let { add(MinecraftHistoryScreenKeys.FILTER_THROUGH, Component.literal(HistoryScreenText.endedAt(it - 1))) }
        filters.ownRankMin?.let { add(MinecraftHistoryScreenKeys.FILTER_MIN_RANK, Component.literal(it.toString())) }
        filters.ownRankMax?.let { add(MinecraftHistoryScreenKeys.FILTER_MAX_RANK, Component.literal(it.toString())) }
        filters.outcome?.let { add(MinecraftHistoryScreenKeys.FILTER_OUTCOME, Component.translatable(it.translationKey)) }
        filters.integrity?.let { add(MinecraftHistoryScreenKeys.FILTER_INTEGRITY, Component.translatable(it.translationKey)) }
        filters.ai?.let { add(MinecraftHistoryScreenKeys.FILTER_AI, Component.translatable(it.translationKey)) }
        return ControlTooltip.list(
            description = Component.translatable(MinecraftHistoryScreenKeys.TOOLTIP_FILTERS),
            title = Component.translatable(MinecraftHistoryScreenKeys.TOOLTIP_FILTERS_APPLIED),
            lines = lines.ifEmpty { listOf(Component.translatable(MinecraftHistoryScreenKeys.FILTER_NONE)) },
            hints = listOf(Component.translatable(MinecraftHistoryScreenKeys.TOOLTIP_FILTERS_HINT).withStyle(ChatFormatting.GRAY)),
        )
    }

    /** 依序在自己的紀錄、管理員用途的全部紀錄與開發環境的壓力測試資料間切換。 */
    private fun cycleScope() {
        if (!session.controller.canRefresh()) return
        if (availableScopes().size < 2) return
        val query = session.controller.state.value.query
        val scope = CycleButtonInput.next(options = availableScopes(), current = query.scope)
        session.filterDraft.setScope(scope)
        session.controller.updateQuery(query.copy(scope = scope).normalized())
    }

    /**
     * 改用指定的排序欄位重新查詢。
     *
     * @param field 選定的排序欄位。
     */
    private fun selectSort(field: HistorySortFieldDto) {
        if (!session.controller.canRefresh()) return
        val query = session.controller.state.value.query
        session.controller.updateQuery(query.copy(sortField = field).normalized())
    }

    /** 切換排序方向。 */
    private fun toggleDirection() {
        if (!session.controller.canRefresh()) return
        val query = session.controller.state.value.query
        val direction = if (query.sortDirection == HistorySortDirectionDto.ASC) HistorySortDirectionDto.DESC else HistorySortDirectionDto.ASC
        session.controller.updateQuery(query.copy(sortDirection = direction).normalized())
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val pointerX = dropdown.pointer(mouseX)
        val pointerY = dropdown.pointer(mouseY)
        renderScreenBackground(context, mouseX, mouseY, delta)
        renderContentBackground(context)
        ScreenRendering.title(context, font, title, width)
        val state = session.controller.state.value
        hoveredEntry = null
        val list = state.list
        val loading = list.status == HistoryBrowseStatus.Loading
        if (cachedQuery != state.query) {
            cachedQuery = state.query
            cachedEntries = emptyList()
        }
        if (list.status == HistoryBrowseStatus.Ready) cachedEntries = list.entries
        val displayEntries = displayEntries()
        val clampedScroll = layout.clampScroll(scroll, contentHeight(displayEntries))
        if (clampedScroll != scroll) {
            session.controller.rememberListPosition(clampedScroll)
        }
        scopeButton?.message = scopeText()
        val canQuery = session.controller.canRefresh()
        scopeButton?.active = canQuery && availableScopes().size > 1
        sortButton?.active = canQuery
        directionButton?.active = canQuery
        filterButton?.tooltip = Tooltip.create(appliedFiltersTooltip())
        sortButton?.message = sortText()
        directionButton?.message = directionText()
        directionButton?.icon = directionIcon()
        scopeButton?.tooltip = Tooltip.create(scopeTooltip())
        sortButton?.tooltip = Tooltip.create(sortTooltip())
        directionButton?.tooltip = Tooltip.create(directionTooltip())
        refreshButton?.tooltip = Tooltip.create(
            Component.translatable(
                when {
                    loading -> MinecraftHistoryScreenKeys.QUERY_LOADING_TOOLTIP
                    session.controller.isRefreshCoolingDown() -> MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP
                    else -> MinecraftHistoryScreenKeys.REFRESH_TOOLTIP
                },
            ),
        )
        previousButton?.active = canQuery && list.previousCursor != null
        nextButton?.active = canQuery && list.nextCursor != null
        refreshButton?.active = session.controller.canRefresh()
        val archiveStatus = session.archiveStatusController.view.value
        retryButton?.visible = list.status is HistoryBrowseStatus.Failed || archiveStatus is HistoryArchiveStatusView.Failed
        refreshButton?.visible = retryButton?.visible != true
        retryButton?.active = if (list.status is HistoryBrowseStatus.Failed) session.controller.canRetry() else canQuery
        val queryHint = when {
            loading -> Component.translatable(MinecraftHistoryScreenKeys.QUERY_LOADING_TOOLTIP)
            session.controller.isRefreshCoolingDown() -> Component.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP)
            else -> null
        }
        listOf(scopeButton to scopeTooltip(), sortButton to sortTooltip(), directionButton to directionTooltip()).forEach { (button, tooltip) ->
            button?.tooltip = Tooltip.create(tooltip.copy().also { text -> queryHint?.let { text.append("\n").append(it.copy().withStyle(ChatFormatting.YELLOW)) } })
        }
        previousButton?.tooltip = Tooltip.create(HistoryPaging.buttonTooltip(MinecraftHistoryScreenKeys.HINT_PREVIOUS_PAGE, MinecraftHistoryScreenKeys.HINT_FIRST_PAGE, queryHint))
        nextButton?.tooltip = Tooltip.create(HistoryPaging.buttonTooltip(MinecraftHistoryScreenKeys.HINT_NEXT_PAGE, MinecraftHistoryScreenKeys.HINT_LAST_PAGE, queryHint))
        retryButton?.tooltip = queryHint?.let(Tooltip::create)
        HistoryScreenText.archiveStatus(archiveStatus)?.let { message ->
            context.drawCenteredString(font, message, width / 2, layout.statusY, 0xffff55)
        }
        context.drawString(font, showLabel(), layout.showLabelX, layout.labelY, LABEL_COLOR)
        context.drawString(font, sortLabel(), layout.sortLabelX, layout.labelY, LABEL_COLOR)
        context.enableScissor(layout.contentLeft, layout.contentTop, layout.contentRight, layout.contentBottom)
        when (val status = list.status) {
            HistoryBrowseStatus.Idle -> context.drawCenteredString(font, Component.translatable(MinecraftHistoryScreenKeys.LOADING), width / 2, layout.contentTop + 12, 0xffffff)
            HistoryBrowseStatus.Loading -> {
                if (displayEntries.isNotEmpty()) renderEntries(context, displayEntries, pointerX, pointerY)
                context.drawCenteredString(font, Component.translatable(MinecraftHistoryScreenKeys.LOADING), width / 2, layout.contentTop + 12, 0xffffff)
            }
            HistoryBrowseStatus.Ready -> if (displayEntries.isEmpty()) {
                context.drawCenteredString(font, Component.translatable(MinecraftHistoryScreenKeys.EMPTY), width / 2, layout.contentTop + 12, 0xffffff)
            } else {
                renderEntries(context, displayEntries, pointerX, pointerY)
            }
            is HistoryBrowseStatus.Failed -> {
                if (displayEntries.isNotEmpty()) renderEntries(context, displayEntries, pointerX, pointerY)
                context.drawCenteredString(font, Component.translatable(status.reason.translationKey), width / 2, layout.contentTop + 12, 0xff6666)
            }
        }
        context.disableScissor()
        renderScrollbar(context, displayEntries)
        if (list.status == HistoryBrowseStatus.Ready && list.entries.isNotEmpty()) {
            HistoryPaging.renderRange(
                context = context,
                textRenderer = font,
                bounds = layout.footerSlot(1),
                primary = Component.translatable(MinecraftHistoryScreenKeys.PAGE_RANGE, list.firstEntryIndex, list.firstEntryIndex + list.entries.size - 1),
                secondary = HistoryPaging.totalText(list.totalCount),
            )
        }
        renderWidgets(context, pointerX, pointerY, delta)
        hoveredEntry?.let { context.renderComponentTooltip(font, it, mouseX, mouseY) }
        dropdown.render(context, font, height, mouseX, mouseY)
    }

    /**
     * 繪製不依賴固定參與者數量的對局卡片。
     *
     * @param context 畫面繪製上下文。
     * @param entries 要繪製的對局摘要。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     */
    private fun renderEntries(context: GuiGraphics, entries: List<HistoryMatchSummaryDto>, mouseX: Int, mouseY: Int) {
        var hover: List<Component>? = null
        var y = layout.contentTop - scroll.toInt()
        entries.forEach { entry ->
            val cardHeight = entryHeight(entry)
            val bounds = layout.cardBounds(y, cardHeight)
            if (y + cardHeight >= layout.contentTop && y <= layout.contentBottom) {
                val hovered = !scrollDragging &&
                    mouseX in bounds.x until bounds.x + bounds.width &&
                    mouseY in layout.contentTop until layout.contentBottom &&
                    mouseY in bounds.y until bounds.y + bounds.height
                context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, if (hovered) CARD_HOVER_BACKGROUND else CARD_BACKGROUND)
                if (hovered) {
                    hover = listOf(
                        Component.translatable(MinecraftHistoryScreenKeys.CARD_TOOLTIP, entry.matchId),
                        Component.translatable(MinecraftHistoryScreenKeys.CARD_OPEN_HINT).withStyle(ChatFormatting.AQUA),
                    )
                }
                val measurement = cardMeasurements.getValue(entry)
                val metadata = measurement.lines
                metadata.forEachIndexed { index, line ->
                    context.drawString(font, line, bounds.x + CARD_PADDING, y + 4 + index * 10, 0xdddddd)
                }
                measurement.inlineDate?.let { date ->
                    context.drawString(font, Component.literal(date), bounds.right - CARD_PADDING - font.width(date), y + 4, 0xaaaaaa)
                }
                val participantTop = y + 6 + metadata.size * 10
                val results = entry.results.associateBy { it.playerId }
                val ownId = Minecraft.getInstance().player?.uuid?.toString()
                val ranked = HistoryScreenText.rankedParticipants(entry)
                val rankWidth = ranked.maxOfOrNull { font.width(results[it.playerId]?.finalRank?.toString() ?: "—") } ?: 0
                val scoreWidth = ranked.maxOfOrNull { font.width(results[it.playerId]?.finalScore?.toString() ?: "—") } ?: 0
                val rankX = bounds.x + CARD_PADDING
                val faceX = rankX + rankWidth + 8
                val nameX = faceX + 14
                val scoreRight = bounds.right - CARD_PADDING
                ranked.forEachIndexed { index, participant ->
                    val rowY = participantTop + index * PARTICIPANT_ROW_HEIGHT
                    RowStripe(
                        left = bounds.x + STRIPE_INSET,
                        top = rowY - 2,
                        right = bounds.right - STRIPE_INSET,
                        bottom = rowY - 2 + PARTICIPANT_ROW_HEIGHT,
                    ).renderIfStriped(context, index)
                    val result = results[participant.playerId]
                    context.drawString(font, Component.literal(result?.finalRank?.toString() ?: "—"), rankX, rowY + 1, 0xcccccc)
                    session.participants.render(participant, context, faceX, rowY, 10, matchId = entry.matchId)
                    val nameWidth = (scoreRight - scoreWidth - 12 - nameX).coerceAtLeast(1)
                    val name = HistoryScreenText.trim(session.participants.name(participant, entry.participants, entry.matchId).string, nameWidth, font::width)
                    context.drawString(font, Component.literal(name), nameX, rowY + 1, if (participant.playerId == ownId) 0x8ed5df else 0xffffff)
                    val score = result?.finalScore?.toString() ?: "—"
                    context.drawString(font, Component.literal(score), scoreRight - font.width(score), rowY + 1, 0xdddddd)
                }
            }
            y += cardHeight
        }
        hoveredEntry = hover
    }

    /**
     * 繪製與拖曳共用幾何的捲軸，不受卡片內容裁切影響。
     *
     * @param context 畫面繪製上下文。
     * @param entries 目前顯示的對局摘要。
     */
    private fun renderScrollbar(context: GuiGraphics, entries: List<HistoryMatchSummaryDto>) {
        val scrollbar = layout.scrollbar(contentHeight(entries), scroll)
        if (scrollbar.maximumScroll == 0 || !layout.hasContent) return
        VerticalScrollbar.render(context, layout.scrollbarBounds.x, scrollbar)
    }

    /**
     * 計算單張對局卡片含下方間距的高度，供繪製與捲軸使用相同幾何資料。
     *
     * @param entry 對局摘要。
     * @return 卡片所需的垂直高度。
     */
    private fun entryHeight(entry: HistoryMatchSummaryDto): Int = cardMeasurements.getValue(entry).height

    /** 同一頁、版面寬度與語言共用已量測的卡片文字。 */
    private var cardMeasurements: Map<HistoryMatchSummaryDto, CardMeasurement> = emptyMap()

    /** 上次量測所用的語言。 */
    private var measurementLanguage: Language? = null

    /** 上次量測所用的卡片文字寬度。 */
    private var measurementWidth = 0

    /** 依目前內容重用卡片量測，翻頁時釋放上一頁的資料。 */
    private fun measureCards(entries: List<HistoryMatchSummaryDto>) {
        val language = Language.getInstance()
        val textWidth = cardTextWidth()
        if (measurementLanguage === language &&
            measurementWidth == textWidth &&
            cardMeasurements.keys.containsAll(entries) &&
            cardMeasurements.size == entries.size
        ) {
            return
        }
        measurementLanguage = language
        measurementWidth = textWidth
        cardMeasurements = entries.associateWith(::measureCard)
    }

    /**
     * 已量測的卡片資訊。
     *
     * @property lines 換行後的文字。
     * @property inlineDate 同列顯示的日期，另起一列時為 null。
     * @property height 卡片及列間距的高度。
     */
    private data class CardMeasurement(val lines: List<FormattedCharSequence>, val inlineDate: String?, val height: Int)

    /** 卡片內文字可用的寬度。 */
    private fun cardTextWidth(): Int = (layout.contentWidth - CARD_PADDING * 2).coerceAtLeast(1)

    /** 量測卡片文字與高度。 */
    private fun measureCard(entry: HistoryMatchSummaryDto): CardMeasurement {
        val status = Component.literal(HistoryScreenText.duration(entry.duration)).append(" • ")
            .append(HistoryScreenText.outcome(entry.outcome))
        if (entry.integrity == HistoryIntegrityFilterDto.INCOMPLETE) {
            status.append(" • ").append(Component.translatable(MinecraftHistoryScreenKeys.INTEGRITY_INCOMPLETE).withStyle(ChatFormatting.YELLOW))
        }
        val rule = HistoryScreenText.rule(entry.ruleId, session.ruleNames)
        val date = HistoryScreenText.endedAt(entry.endedAtEpochMillis)
        val inline = font.width(rule) + font.width(date) + 24 <= cardTextWidth()
        val header = if (inline) listOf(rule) else listOf(rule, Component.literal(date).withStyle(ChatFormatting.GRAY))
        val details = Component.translatable(MinecraftHistoryScreenKeys.ROUNDS, entry.roundCount ?: "—").append(" • ").append(status)
        val lines = (header + details).flatMap { font.split(it, cardTextWidth()) }
        val height = CARD_VERTICAL_PADDING + lines.size * 10 + entry.participants.size * PARTICIPANT_ROW_HEIGHT + HistoryScreenLayout.CARD_GAP
        return CardMeasurement(lines = lines, inlineDate = date.takeIf { inline }, height = height)
    }

    /** 下拉清單展開時，暫停底層控制項的提示框。 */
    override fun setTooltipForNextRenderPass(lines: List<FormattedCharSequence>, positioner: ClientTooltipPositioner, focused: Boolean) {
        if (!dropdown.isOpen) super.setTooltipForNextRenderPass(lines, positioner, focused)
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        session.close()
    }

    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /** 工具列與卡片的色彩與尺寸。 */
    private companion object {
        /** 排序欄位選擇器的識別值。 */
        const val SORT_SELECTOR_KEY = "sort"

        /** 工具列標籤的文字顏色。 */
        const val LABEL_COLOR: Int = 0xCCCCCC

        /** 卡片內文字與卡片左右緣的距離。 */
        const val CARD_PADDING: Int = 6

        /** 卡片內文字上方與參與者列下方留白的合計高度。 */
        const val CARD_VERTICAL_PADDING: Int = 10

        /** 參與者列的高度。 */
        const val PARTICIPANT_ROW_HEIGHT: Int = 14

        /** 參與者列隔列底色與卡片左右緣的距離。 */
        const val STRIPE_INSET: Int = 2

        /** 卡片一般背景。 */
        const val CARD_BACKGROUND: Int = 0x88333333.toInt()

        /** 游標停留時稍亮的半透明卡片背景。 */
        const val CARD_HOVER_BACKGROUND: Int = 0x99505050.toInt()
    }
}
