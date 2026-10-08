package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortFieldDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.CycleButtonInput
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 歷史對局分頁列表；查詢生命週期由共用瀏覽 session 管理。
 *
 * @property session 此畫面所屬的共用瀏覽 session。
 */
internal class HistoryListScreen(
    private val session: HistoryBrowseSession,
) : Screen(Text.translatable(MinecraftHistoryScreenKeys.TITLE)) {
    /** 目前版面幾何。 */
    private var layout = HistoryScreenLayout.measure(0, 0)

    /** 目前列表捲動位置。 */
    private var scroll = 0.0

    /** 上一頁按鈕。 */
    private var previousButton: ButtonWidget? = null

    /** 下一頁按鈕。 */
    private var nextButton: ButtonWidget? = null

    /** 重新查詢按鈕。 */
    private var refreshButton: ButtonWidget? = null

    /** 查詢失敗時的重試按鈕。 */
    private var retryButton: ButtonWidget? = null

    /** 紀錄範圍按鈕。 */
    private var scopeButton: ButtonWidget? = null

    /** 排序欄位按鈕。 */
    private var sortButton: ButtonWidget? = null

    /** 排序方向按鈕。 */
    private var directionButton: ButtonWidget? = null

    /** 已套用條件的篩選入口。 */
    private var filterButton: ButtonWidget? = null

    /** 是否正在拖曳捲軸。 */
    private var scrollDragging = false

    /** 拖曳時游標在捲軸滑塊內的抓取偏移。 */
    private var scrollGrabOffset = 0.0

    /** 查詢載入期間仍可顯示的上一批資料。 */
    private var cachedEntries: List<HistoryMatchSummaryDto> = emptyList()

    /** 本幀滑過的卡片提示，待內容區 scissor 關閉後繪製。 */
    private var hoveredEntry: List<Text>? = null

    /** 暫存卡片所屬條件，避免切換範圍或空結果沿用舊資料。 */
    private var cachedQuery: HistoryBrowseQuery? = null

    override fun init() {
        layout = HistoryScreenLayout.measure(width, height)
        scroll = session.controller.state.value.list.scrollOffset
        scrollDragging = false
        clearChildren()
        val toolbar = layout.toolbarBounds(width)
        filterButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.FILTERS)) { session.openFilters() }.dimensions(toolbar[0].x, toolbar[0].y, toolbar[0].width, 20).build().also(::addDrawableChild)
        scopeButton = ButtonWidget.builder(scopeText()) { cycleScope() }.dimensions(toolbar[1].x, toolbar[1].y, toolbar[1].width, 20).build().also(::addDrawableChild)
        sortButton = ButtonWidget.builder(sortText()) { cycleSort() }.dimensions(toolbar[2].x, toolbar[2].y, toolbar[2].width, 20).build().also(::addDrawableChild)
        directionButton = ButtonWidget.builder(directionText()) { toggleDirection() }.dimensions(toolbar[3].x, toolbar[3].y, toolbar[3].width, 20).build().also(::addDrawableChild)
        refreshButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.REFRESH)) { session.controller.refresh() }.dimensions(toolbar[4].x, toolbar[4].y, toolbar[4].width, 20).build().also(::addDrawableChild)
        val footer = layout.footerBounds(width, layout.footerTop)
        previousButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.PREVIOUS)) { session.controller.previousPage() }.dimensions(footer[0].x, footer[0].y, footer[0].width, 20).build().also(::addDrawableChild)
        nextButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.NEXT)) { session.controller.nextPage() }.dimensions(footer[2].x, footer[2].y, footer[2].width, 20).build().also(::addDrawableChild)
        retryButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.RETRY)) {
            if (session.archiveStatusController.view.value is HistoryArchiveStatusView.Failed) {
                session.retryArchiveStatus()
            } else {
                session.controller.retry()
            }
        }.dimensions(toolbar[4].x, toolbar[4].y, toolbar[4].width, 20).build().also(::addDrawableChild)
        addDrawableChild(ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.CLOSE)) { close() }.dimensions(footer[3].x, footer[3].y, footer[3].width, 20).build())
    }

    override fun resize(client: MinecraftClient, width: Int, height: Int) {
        super.resize(client, width, height)
        session.controller.rememberListPosition(scroll)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (mouseY < layout.contentTop || mouseY > layout.contentBottom) return super.mouseScrolled(mouseX, mouseY, amount)
        scroll = layout.clampScroll(scroll - amount * 18.0, contentHeight(displayEntries()))
        session.controller.rememberListPosition(scroll)
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val scrollbar = layout.scrollbar(contentHeight(displayEntries()), scroll)
        val bounds = layout.scrollbarBounds(width)
        if (button == 0 &&
            scrollbar.maximumScroll > 0 &&
            mouseX >= bounds.x &&
            mouseX < bounds.x + bounds.width &&
            mouseY >= bounds.y &&
            mouseY < bounds.y + bounds.height
        ) {
            scrollDragging = true
            scrollGrabOffset = scrollbar.grabOffset(mouseY)
            updateDragScroll(mouseY)
            return true
        }
        val list = session.controller.state.value.list
        if (button == 0 && !scrollDragging && list.status == HistoryBrowseStatus.Ready) {
            val index = layout.cardIndexAt(width, mouseX, mouseY, scroll, list.entries.map(::entryHeight))
            if (index != null) {
                val matchId = list.entries[index].matchId
                session.controller.rememberListPosition(scroll, matchId)
                if (session.openSummary(matchId)) return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
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

    /** 依拖曳位置更新列表捲動偏移。
     *
     * @param mouseY 游標垂直座標。
     */
    private fun updateDragScroll(mouseY: Double) {
        val scrollbar = layout.scrollbar(contentHeight(displayEntries()), scroll)
        scroll = scrollbar.scrollIndexFor(mouseY, scrollGrabOffset).toDouble()
        session.controller.rememberListPosition(scroll)
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
     * 計算清單高度，包含第一張卡片前的留白。
     *
     * @param entries 目前顯示的對局摘要。
     * @return 捲動內容的總像素高度。
     */
    private fun contentHeight(entries: List<HistoryMatchSummaryDto>): Int = entries.sumOf(::entryHeight) + if (entries.isEmpty()) 0 else 4

    /** 取得目前紀錄範圍的按鈕文字。 */
    private fun scopeText(): Text = Text.translatable("${MinecraftHistoryScreenKeys.SCOPE_PREFIX}${session.controller.state.value.query.scope.name.lowercase()}")

    /** 取得目前排序欄位的按鈕文字。 */
    private fun sortText(): Text = Text.translatable("${MinecraftHistoryScreenKeys.SORT_PREFIX}${session.controller.state.value.query.sortField.name.lowercase()}")

    /** 取得目前排序方向的按鈕文字。 */
    private fun directionText(): Text = Text.translatable("${MinecraftHistoryScreenKeys.DIRECTION_PREFIX}${session.controller.state.value.query.sortDirection.name.lowercase()}")

    /** 建立列出目前值與可選值的 tooltip。
     *
     * @param title 選項名稱。
     * @param current 目前選值。
     * @param options 可選值。
     * @return 格式化後的 tooltip 文字。
     */
    private fun optionTooltip(title: Text, current: Text, options: List<Text>): Text = Text.empty()
        .append(Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_DESCRIPTION, title).formatted(Formatting.GRAY))
        .append("\n")
        .append(Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_CURRENT, current).formatted(Formatting.GREEN))
        .append("\n")
        .append(Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_OPTIONS).formatted(Formatting.GOLD))
        .also { result -> options.forEach { option -> result.append("\n• ").append(option.copy().formatted(if (option.string == current.string) Formatting.GREEN else Formatting.WHITE)) } }

    /** 建立紀錄範圍按鈕的選項 tooltip。 */
    private fun scopeTooltip(): Text = optionTooltip(Text.translatable(MinecraftHistoryScreenKeys.SCOPE_TITLE), scopeText(), availableScopes().map { Text.translatable("${MinecraftHistoryScreenKeys.SCOPE_PREFIX}${it.name.lowercase()}") })

    /** 只提供最近權威回應允許的查詢範圍。 */
    private fun availableScopes(): List<HistoryQueryScopeDto> = buildList {
        val state = session.controller.state.value
        add(HistoryQueryScopeDto.OWN)
        if (state.allowAll) add(HistoryQueryScopeDto.ALL)
        if (state.allowStressTest) add(HistoryQueryScopeDto.STRESS_TEST)
    }

    /** 全部紀錄與壓力測試資料沒有個人名次或個人分數排序。 */
    private fun availableSortFields(): List<HistorySortFieldDto> = if (session.controller.state.value.query.scope.coversAllMatches) listOf(HistorySortFieldDto.ENDED_AT, HistorySortFieldDto.DURATION) else HistorySortFieldDto.entries

    /** 建立排序欄位按鈕的選項 tooltip。 */
    private fun sortTooltip(): Text = optionTooltip(Text.translatable(MinecraftHistoryScreenKeys.SORT_TITLE), sortText(), availableSortFields().map { Text.translatable("${MinecraftHistoryScreenKeys.SORT_PREFIX}${it.name.lowercase()}") })

    /** 建立排序方向按鈕的選項 tooltip。 */
    private fun directionTooltip(): Text = optionTooltip(Text.translatable(MinecraftHistoryScreenKeys.DIRECTION_TITLE), directionText(), HistorySortDirectionDto.entries.map { Text.translatable("${MinecraftHistoryScreenKeys.DIRECTION_PREFIX}${it.name.lowercase()}") })

    /** 僅列出已套用條件，不將尚未通過驗證的草稿混入。 */
    private fun appliedFiltersTooltip(): Text {
        val filters = session.controller.state.value.query.filters
        val lines = mutableListOf<Text>()

        /** 將欄位名稱與已套用值加入提示。 */
        fun add(key: String, value: Text) {
            lines += Text.translatable(key).copy().append(": ").append(value)
        }
        filters.ruleId?.let { add(MinecraftHistoryScreenKeys.FILTER_RULE, HistoryScreenText.rule(it, session.ruleNames)) }
        filters.playerName?.let { add(MinecraftHistoryScreenKeys.FILTER_PLAYER_NAME, Text.literal(it)) }
        filters.matchId?.let { add(MinecraftHistoryScreenKeys.FILTER_MATCH_ID, Text.literal(it)) }
        filters.endedAtFromEpochMillis?.let { add(MinecraftHistoryScreenKeys.FILTER_FROM, Text.literal(HistoryScreenText.endedAt(it))) }
        filters.endedAtBeforeEpochMillis?.let { add(MinecraftHistoryScreenKeys.FILTER_THROUGH, Text.literal(HistoryScreenText.endedAt(it - 1))) }
        filters.ownRankMin?.let { add(MinecraftHistoryScreenKeys.FILTER_MIN_RANK, Text.literal(it.toString())) }
        filters.ownRankMax?.let { add(MinecraftHistoryScreenKeys.FILTER_MAX_RANK, Text.literal(it.toString())) }
        filters.outcome?.let { add(MinecraftHistoryScreenKeys.FILTER_OUTCOME, Text.translatable("${MinecraftHistoryScreenKeys.FILTER_OUTCOME}.${it.name.lowercase()}")) }
        filters.integrity?.let { add(MinecraftHistoryScreenKeys.FILTER_INTEGRITY, Text.translatable("${MinecraftHistoryScreenKeys.FILTER_INTEGRITY}.${it.name.lowercase()}")) }
        filters.ai?.let { add(MinecraftHistoryScreenKeys.FILTER_AI, Text.translatable("${MinecraftHistoryScreenKeys.FILTER_AI}.${it.name.lowercase()}")) }
        return Text.translatable(MinecraftHistoryScreenKeys.TOOLTIP_FILTERS).copy().also { text ->
            if (lines.isEmpty()) {
                text.append("\n").append(Text.translatable(MinecraftHistoryScreenKeys.FILTER_NONE))
            } else {
                lines.forEach { text.append("\n• ").append(it) }
            }
        }
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

    /** 循環切換有限排序欄位。 */
    private fun cycleSort() {
        if (!session.controller.canRefresh()) return
        val query = session.controller.state.value.query
        val fields = availableSortFields()
        val next = CycleButtonInput.next(options = fields, current = query.sortField)
        session.controller.updateQuery(query.copy(sortField = next).normalized())
    }

    /** 切換排序方向。 */
    private fun toggleDirection() {
        if (!session.controller.canRefresh()) return
        val query = session.controller.state.value.query
        val direction = if (query.sortDirection == HistorySortDirectionDto.ASC) HistorySortDirectionDto.DESC else HistorySortDirectionDto.ASC
        session.controller.updateQuery(query.copy(sortDirection = direction).normalized())
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 2, Formatting.WHITE.colorValue ?: 0xffffff)
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
            scroll = clampedScroll
            session.controller.rememberListPosition(scroll)
        }
        scopeButton?.message = scopeText()
        val canQuery = session.controller.canRefresh()
        scopeButton?.active = canQuery && availableScopes().size > 1
        sortButton?.active = canQuery
        directionButton?.active = canQuery
        filterButton?.tooltip = Tooltip.of(appliedFiltersTooltip())
        sortButton?.message = sortText()
        directionButton?.message = directionText()
        scopeButton?.tooltip = Tooltip.of(scopeTooltip())
        sortButton?.tooltip = Tooltip.of(CycleButtonInput.withHint(tooltip = sortTooltip(), optionCount = availableSortFields().size, active = sortButton?.active == true))
        directionButton?.tooltip = Tooltip.of(directionTooltip())
        refreshButton?.tooltip = Tooltip.of(
            Text.translatable(
                when {
                    loading -> MinecraftHistoryScreenKeys.QUERY_LOADING_TOOLTIP
                    session.controller.isRefreshCoolingDown() -> MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP
                    else -> MinecraftHistoryScreenKeys.REFRESH_TOOLTIP
                },
            ),
        )
        previousButton?.active = canQuery && list.pageNumber > 1
        nextButton?.active = canQuery && list.nextCursor != null
        refreshButton?.active = session.controller.canRefresh()
        val archiveStatus = session.archiveStatusController.view.value
        retryButton?.visible = list.status is HistoryBrowseStatus.Failed || archiveStatus is HistoryArchiveStatusView.Failed
        refreshButton?.visible = retryButton?.visible != true
        retryButton?.active = canQuery
        val queryHint = when {
            loading -> Text.translatable(MinecraftHistoryScreenKeys.QUERY_LOADING_TOOLTIP)
            session.controller.isRefreshCoolingDown() -> Text.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP)
            else -> null
        }
        listOf(scopeButton to scopeTooltip(), sortButton to sortTooltip(), directionButton to directionTooltip()).forEach { (button, tooltip) ->
            button?.tooltip = Tooltip.of(tooltip.copy().also { text -> queryHint?.let { text.append("\n").append(it.copy().formatted(Formatting.YELLOW)) } })
        }
        previousButton?.tooltip = queryHint?.let(Tooltip::of)
        nextButton?.tooltip = queryHint?.let(Tooltip::of)
        retryButton?.tooltip = queryHint?.let(Tooltip::of)
        HistoryScreenText.archiveStatus(archiveStatus)?.let { message ->
            context.drawCenteredTextWithShadow(textRenderer, message, width / 2, (layout.contentTop - 14).coerceAtLeast(2), 0xffff55)
        }
        context.enableScissor(8, layout.contentTop, width - 8, layout.contentBottom)
        when (val status = list.status) {
            HistoryBrowseStatus.Idle -> context.drawCenteredTextWithShadow(textRenderer, Text.translatable(MinecraftHistoryScreenKeys.LOADING), width / 2, layout.contentTop + 12, 0xffffff)
            HistoryBrowseStatus.Loading -> {
                if (displayEntries.isNotEmpty()) renderEntries(context, displayEntries, mouseX, mouseY)
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable(MinecraftHistoryScreenKeys.LOADING), width / 2, layout.contentTop + 12, 0xffffff)
            }
            HistoryBrowseStatus.Ready -> if (displayEntries.isEmpty()) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable(MinecraftHistoryScreenKeys.EMPTY), width / 2, layout.contentTop + 12, 0xffffff)
            } else {
                renderEntries(context, displayEntries, mouseX, mouseY)
            }
            is HistoryBrowseStatus.Failed -> {
                if (displayEntries.isNotEmpty()) renderEntries(context, displayEntries, mouseX, mouseY)
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("${MinecraftHistoryScreenKeys.FAILURE_PREFIX}${status.reason.name.lowercase()}"), width / 2, layout.contentTop + 12, 0xff6666)
            }
        }
        context.disableScissor()
        renderScrollbar(context, displayEntries)
        val pageBounds = layout.footerBounds(width, layout.footerTop)[1]
        val pageLabel = Text.translatable(MinecraftHistoryScreenKeys.PAGE, list.pageNumber)
        val pageText = HistoryScreenText.trim(pageLabel.string, pageBounds.width, textRenderer::getWidth)
        val showRange = list.status == HistoryBrowseStatus.Ready && list.entries.isNotEmpty()
        val rangeLabel = if (showRange) Text.translatable(MinecraftHistoryScreenKeys.PAGE_RANGE, list.firstEntryIndex, list.firstEntryIndex + list.entries.size - 1) else null
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(pageText), pageBounds.x + pageBounds.width / 2, pageBounds.y + if (showRange) 1 else 6, 0xffffff)
        rangeLabel?.let {
            val rangeText = HistoryScreenText.trim(it.string, pageBounds.width, textRenderer::getWidth)
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(rangeText), pageBounds.x + pageBounds.width / 2, pageBounds.y + 11, 0xaaaaaa)
        }
        super.render(context, mouseX, mouseY, delta)
        if (rangeLabel != null && mouseX in pageBounds.x until pageBounds.x + pageBounds.width && mouseY in pageBounds.y until pageBounds.y + pageBounds.height) {
            context.drawTooltip(textRenderer, listOf(pageLabel, rangeLabel), mouseX, mouseY)
        }
        hoveredEntry?.let { context.drawTooltip(textRenderer, it, mouseX, mouseY) }
    }

    /** 繪製不依賴固定參與者數量的對局卡片。
     *
     * @param context 畫面繪製上下文。
     * @param entries 要繪製的對局摘要。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     */
    private fun renderEntries(context: DrawContext, entries: List<HistoryMatchSummaryDto>, mouseX: Int, mouseY: Int) {
        var hover: List<Text>? = null
        var y = layout.contentTop + 4 - scroll.toInt()
        val scrollbarVisible = layout.maximumScroll(contentHeight(entries)) > 0
        entries.forEach { entry ->
            val cardHeight = entryHeight(entry)
            val bounds = layout.cardBounds(width, y, cardHeight, scrollbarVisible)
            if (y + cardHeight >= layout.contentTop && y <= layout.contentBottom) {
                val hovered = !scrollDragging &&
                    mouseX in bounds.x until bounds.x + bounds.width &&
                    mouseY in layout.contentTop until layout.contentBottom &&
                    mouseY in bounds.y until bounds.y + bounds.height
                context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, if (hovered) CARD_HOVER_BACKGROUND else CARD_BACKGROUND)
                if (hovered) {
                    hover = listOf(
                        Text.translatable(MinecraftHistoryScreenKeys.CARD_TOOLTIP, entry.matchId),
                        Text.translatable(MinecraftHistoryScreenKeys.CARD_OPEN_HINT).formatted(Formatting.AQUA),
                    )
                }
                val metadata = metadataLines(entry)
                metadata.forEachIndexed { index, line ->
                    context.drawTextWithShadow(textRenderer, line, 16, y + 4 + index * 10, 0xdddddd)
                }
                if (inlineHeader(entry)) {
                    val date = HistoryScreenText.endedAt(entry.endedAtEpochMillis)
                    context.drawTextWithShadow(textRenderer, Text.literal(date), bounds.x + bounds.width - 10 - textRenderer.getWidth(date), y + 4, 0xaaaaaa)
                }
                val participantTop = y + 6 + metadata.size * 10
                val results = entry.results.associateBy { it.playerId }
                val ownId = MinecraftClient.getInstance().player?.uuid?.toString()
                val ranked = HistoryScreenText.rankedParticipants(entry)
                val rankWidth = ranked.maxOfOrNull { textRenderer.getWidth(results[it.playerId]?.finalRank?.toString() ?: "—") } ?: 0
                val scoreWidth = ranked.maxOfOrNull { textRenderer.getWidth(results[it.playerId]?.finalScore?.toString() ?: "—") } ?: 0
                val faceX = 16 + rankWidth + 8
                val nameX = faceX + 14
                val scoreRight = bounds.x + bounds.width - 10
                ranked.forEachIndexed { index, participant ->
                    val rowY = participantTop + index * 14
                    val result = results[participant.playerId]
                    context.drawTextWithShadow(textRenderer, Text.literal(result?.finalRank?.toString() ?: "—"), 16, rowY + 1, 0xcccccc)
                    session.participants.render(participant, context, faceX, rowY, 10)
                    val nameWidth = (scoreRight - scoreWidth - 12 - nameX).coerceAtLeast(1)
                    val name = HistoryScreenText.trim(session.participants.name(participant, entry.participants).string, nameWidth, textRenderer::getWidth)
                    context.drawTextWithShadow(textRenderer, Text.literal(name), nameX, rowY + 1, if (participant.playerId == ownId) 0x8ed5df else 0xffffff)
                    val score = result?.finalScore?.toString() ?: "—"
                    context.drawTextWithShadow(textRenderer, Text.literal(score), scoreRight - textRenderer.getWidth(score), rowY + 1, 0xdddddd)
                }
            }
            y += cardHeight
        }
        hoveredEntry = hover
    }

    /** 繪製與拖曳共用幾何的捲軸，不受卡片內容裁切影響。
     *
     * @param context 畫面繪製上下文。
     * @param entries 目前顯示的對局摘要。
     */
    private fun renderScrollbar(context: DrawContext, entries: List<HistoryMatchSummaryDto>) {
        val scrollbar = layout.scrollbar(contentHeight(entries), scroll)
        if (scrollbar.maximumScroll == 0 || !layout.hasContent) return
        val bounds = layout.scrollbarBounds(width)
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, SCROLLBAR_TRACK_COLOR)
        context.fill(bounds.x, scrollbar.thumbTop, bounds.x + bounds.width, scrollbar.thumbTop + scrollbar.thumbHeight, SCROLLBAR_THUMB_COLOR)
    }

    /** 計算單張對局卡片高度，供繪製與捲軸使用相同幾何資料。
     *
     * @param entry 對局摘要。
     * @return 卡片所需的垂直高度。
     */
    private fun entryHeight(entry: HistoryMatchSummaryDto): Int = 12 + metadataLines(entry).size * 10 + entry.participants.size * 14

    /**
     * 判斷規則與日期是否可在同一列左右對齊。
     *
     * @param entry 已保存對局摘要。
     * @return 是否有足夠寬度保留標題與日期間距。
     */
    private fun inlineHeader(entry: HistoryMatchSummaryDto): Boolean = textRenderer.getWidth(HistoryScreenText.rule(entry.ruleId, session.ruleNames)) + textRenderer.getWidth(HistoryScreenText.endedAt(entry.endedAtEpochMillis)) + 24 <= width - 46

    /**
     * 按實際字寬換行卡片資訊，避免日期、時長與個人成績互相覆蓋。
     *
     * @param entry 對局摘要。
     * @return 已按目前可用寬度排列的資訊文字行。
     */
    private fun metadataLines(entry: HistoryMatchSummaryDto): List<OrderedText> {
        val status = Text.literal(HistoryScreenText.duration(entry.durationMillis)).append(" • ")
            .append(HistoryScreenText.outcome(entry.outcome))
        if (entry.integrity == HistoryIntegrityFilterDto.INCOMPLETE) {
            status.append(" • ").append(Text.translatable(MinecraftHistoryScreenKeys.INTEGRITY_INCOMPLETE).formatted(Formatting.YELLOW))
        }
        val rule = HistoryScreenText.rule(entry.ruleId, session.ruleNames)
        val date = Text.literal(HistoryScreenText.endedAt(entry.endedAtEpochMillis)).formatted(Formatting.GRAY)
        val header = if (inlineHeader(entry)) listOf(rule) else listOf(rule, date)
        val details = Text.translatable(MinecraftHistoryScreenKeys.ROUNDS, entry.roundCount ?: "—").append(" • ").append(status)
        return (header + details).flatMap { textRenderer.wrapLines(it, (width - 42).coerceAtLeast(1)) }
    }

    override fun shouldPause(): Boolean = false

    override fun close() {
        session.close()
    }

    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /** 卡片與捲軸的共用視覺色彩。 */
    private companion object {
        /** 卡片一般背景。 */
        const val CARD_BACKGROUND: Int = 0x88333333.toInt()

        /** 游標停留時稍亮的半透明卡片背景。 */
        const val CARD_HOVER_BACKGROUND: Int = 0x99505050.toInt()

        /** 與其他畫面一致的捲軸軌道顏色。 */
        const val SCROLLBAR_TRACK_COLOR: Int = 0x80505050.toInt()

        /** 與其他畫面一致的捲軸滑塊顏色。 */
        const val SCROLLBAR_THUMB_COLOR: Int = 0xFFD0D0D0.toInt()
    }
}
