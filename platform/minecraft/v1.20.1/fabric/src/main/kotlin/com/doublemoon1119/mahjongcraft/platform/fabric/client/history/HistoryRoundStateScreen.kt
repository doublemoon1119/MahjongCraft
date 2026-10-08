package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailValueDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import kotlin.math.ceil

/**
 * 顯示初始或交易後的唯讀牌面，以及該筆結算保存的完整和牌手牌。
 * @property session 共用查詢、導航及呈現來源。
 */
internal class HistoryRoundStateScreen(
    private val session: HistoryBrowseSession,
) : Screen(Text.translatable(MinecraftHistoryScreenKeys.STATE_TITLE)) {
    /** 固定區、內容及捲軸幾何。 */
    private var layout = HistoryRoundEventsLayout.measure(0, 0)

    /** 目前捲動位置。 */
    private var scroll = 0.0

    /** 已顯示的位置，避免切換時沿用不同交易的捲動。 */
    private var displayedPosition: HistoryRoundPositionDto? = null

    /** 是否拖曳捲軸。 */
    private var dragging = false

    /** 滑塊抓取偏移。 */
    private var grabOffset = 0.0

    /** 牌牆是否展開。 */
    private var wallExpanded = false

    /** 前後及重試控制項。 */
    private val queryButtons = mutableListOf<ButtonWidget>()

    /** 只在牌面讀取失敗時顯示的重試控制項。 */
    private lateinit var retryButton: ButtonWidget

    /** 共用牌面 renderer。 */
    private val groups = HistoryTileGroupRenderer(session.tileFaces, session.tileAssets, session.discardMarkers)

    /** 共用規則欄位標題與文字呈現。 */
    private val events = HistoryRoundEventPresenter(session.actionVocabulary, session.exhaustiveDrawReasons, session.roundOutcomes)

    /** 建立固定控制項，恢復同一狀態的捲動位置。 */
    override fun init() {
        layout = HistoryRoundEventsLayout.measure(width, height, 20)
        scroll = currentRound()?.stateScrollOffset ?: 0.0
        displayedPosition = currentRound()?.confirmedPosition
        queryButtons.clear()
        clearChildren()
        val bounds = layout.footerBounds(width)
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.BACK)) { session.backToRound() }
                .dimensions(bounds[0].x, bounds[0].y, bounds[0].width, 20).build(),
        )
        queryButtons += addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.PREVIOUS)) { session.controller.previousRoundState() }
                .dimensions(bounds[1].x, bounds[1].y, bounds[1].width, 20).build(),
        )
        queryButtons += addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.NEXT)) { session.controller.nextRoundState() }
                .dimensions(bounds[3].x, bounds[3].y, bounds[3].width, 20).build(),
        )
        retryButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.RETRY)) { session.controller.retry() }
            .dimensions(0, 0, 1, 20).build()
        retryButton.visible = false
        addDrawableChild(retryButton)
    }

    /** 歷史瀏覽不暫停整合伺服器。 */
    override fun shouldPause(): Boolean = false

    /** 返回目前局事件，保留該頁位置。 */
    override fun close() {
        session.backToRound()
    }

    /** 正常子頁切換不結束共用 session。 */
    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /** 更新連線生命週期。 */
    override fun tick() {
        session.tick()
        super.tick()
    }

    /**
     * 繪製經驗證桌況；載入／錯誤期間不把上一筆資料冒充新位置。
     * @param context 繪製上下文。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param delta 幀內插值。
     */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val round = currentRound()
        val status = round?.stateStatus ?: HistoryBrowseStatus.Failed(HistoryBrowseFailure.NOT_AVAILABLE)
        val state = round?.state?.takeIf { status == HistoryBrowseStatus.Ready && it.position == round.confirmedPosition }
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 2, TEXT_COLOR)
        val canQuery = session.controller.canQueryRound()
        queryButtons[0].active = canQuery && state != null && state.position != HistoryRoundPositionDto.Initial
        queryButtons[1].active = canQuery &&
            state != null &&
            when (val position = state.position) {
                HistoryRoundPositionDto.Initial -> round.lastTransactionIndex != null || round.knownTransactionIndices.isNotEmpty()
                is HistoryRoundPositionDto.AfterTransaction -> round.lastTransactionIndex?.let { position.index < it } ?: true
            }
        retryButton.visible = false
        retryButton.active = false
        val queryTooltip = when {
            session.controller.isRefreshCoolingDown() -> Tooltip.of(Text.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP))
            !canQuery -> Tooltip.of(Text.translatable(MinecraftHistoryScreenKeys.QUERY_LOADING_TOOLTIP))
            else -> null
        }
        queryButtons.forEach { it.tooltip = queryTooltip }
        val rows = state?.let(::rows).orEmpty()
        val contentHeight = rows.sumOf { it.height } + 8
        if (state != null) {
            if (displayedPosition != state.position) {
                displayedPosition = state.position
                scroll = round.stateScrollOffset
                dragging = false
            }
            scroll = layout.clampScroll(scroll, contentHeight)
            session.controller.rememberRoundPosition(scroll)
        }
        var tooltip: List<Text>? = null
        context.enableScissor(layout.left, layout.contentTop, layout.cardRight, layout.contentBottom)
        if (state == null) {
            val message = if (status is HistoryBrowseStatus.Failed) Text.translatable("${MinecraftHistoryScreenKeys.FAILURE_PREFIX}${status.reason.name.lowercase()}") else Text.translatable(MinecraftHistoryScreenKeys.LOADING)
            val lines = textRenderer.wrapLines(message, (layout.contentWidth - 16).coerceAtLeast(1)).take(
                ((layout.contentBottom - layout.contentTop - 32) / LINE_HEIGHT).coerceAtLeast(1),
            )
            lines.forEachIndexed { index, line ->
                context.drawTextWithShadow(textRenderer, line, layout.left + 8, layout.contentTop + 8 + index * LINE_HEIGHT, TEXT_COLOR)
            }
            if (status is HistoryBrowseStatus.Failed) {
                val retryWidth = 80.coerceAtMost((layout.contentWidth - 16).coerceAtLeast(1))
                val retryX = layout.cardRight - retryWidth
                val retryY = layout.contentTop + 8 + lines.size * LINE_HEIGHT + 4
                retryButton.x = retryX
                retryButton.y = retryY
                retryButton.width = retryWidth
                retryButton.visible = true
                retryButton.active = session.controller.canRetry()
            }
        } else {
            var y = layout.contentTop + 4 - scroll.toInt()
            rows.forEach { row ->
                if (y + row.height >= layout.contentTop && y < layout.contentBottom) {
                    val hover = !dragging && layout.containsCard(mouseX.toDouble(), mouseY.toDouble(), y, row.height)
                    when (row) {
                        is Row.Line -> {
                            row.participant?.let { session.participants.render(it, context, layout.left + 8, y, 10) }
                            context.drawTextWithShadow(textRenderer, row.text, layout.left + row.indent, y, row.color)
                            if (hover) tooltip = listOf(row.fullText)
                        }
                        is Row.Group -> {
                            context.fill(layout.left, y, layout.cardRight, y + row.height - 4, if (hover) HOVER_COLOR else CARD_COLOR)
                            val hit = groups.render(context, row.layout, state.tileCatalog, layout.left + 8, y + 4, mouseX, mouseY, row.taken, row.markers)
                            if (hover) tooltip = hit?.plus(row.hint?.let(::listOf).orEmpty())
                        }
                        is Row.HandHeading -> Unit
                        is Row.PlayerCard -> {
                            context.fill(layout.left, y, layout.cardRight, y + row.height - 4, if (hover) HOVER_COLOR else CARD_COLOR)
                            var nestedY = y + 8
                            row.rows.forEach { nested ->
                                val nestedHover = !dragging && layout.containsCard(mouseX.toDouble(), mouseY.toDouble(), nestedY, nested.height)
                                when (nested) {
                                    is Row.Line -> {
                                        nested.participant?.let { session.participants.render(it, context, layout.left + 16, nestedY, 10) }
                                        context.drawTextWithShadow(textRenderer, nested.text, layout.left + nested.indent + 8, nestedY, nested.color)
                                        if (nestedHover) tooltip = listOf(nested.fullText)
                                    }
                                    is Row.HandHeading -> {
                                        val textX = layout.left + nested.indent + 8
                                        context.drawTextWithShadow(textRenderer, nested.label, textX, nestedY, nested.color)
                                        val actionX = textX + textRenderer.getWidth(nested.label) + 4
                                        val actionHover = nested.actionEnabled && nestedHover && mouseX >= actionX && mouseX < actionX + textRenderer.getWidth(nested.action)
                                        val actionColor: Int = when {
                                            actionHover -> SORT_HOVER_COLOR
                                            nested.actionEnabled -> SORT_COLOR
                                            else -> MUTED_COLOR
                                        }
                                        context.drawTextWithShadow(
                                            textRenderer,
                                            nested.action.asOrderedText(),
                                            actionX,
                                            nestedY,
                                            actionColor,
                                        )
                                        if (actionHover || (!nested.actionEnabled && nestedHover)) tooltip = listOf(nested.tooltip)
                                    }
                                    is Row.Group -> {
                                        val hit = groups.render(context, nested.layout, state.tileCatalog, layout.left + 16, nestedY + 4, mouseX, mouseY, nested.taken, nested.markers)
                                        if (nestedHover) tooltip = hit?.plus(nested.hint?.let(::listOf).orEmpty())
                                    }
                                    is Row.Space, is Row.PlayerCard, is Row.WallSection -> Unit
                                }
                                nestedY += nested.height
                            }
                        }
                        is Row.WallSection -> {
                            context.fill(layout.left, y, layout.cardRight, y + row.height - 4, if (hover) HOVER_COLOR else CARD_COLOR)
                            val label = Text.translatable(MinecraftHistoryScreenKeys.STATE_WALL_SECTION, row.wallCount, row.reservedCount)
                            context.drawTextWithShadow(textRenderer, if (row.expanded) Text.literal("▼") else Text.literal("▶"), layout.left + 8, y + 4, ACCENT_COLOR)
                            textRenderer.wrapLines(label, (layout.contentWidth - 40).coerceAtLeast(1)).forEachIndexed { index, line ->
                                context.drawTextWithShadow(textRenderer, line, layout.left + 22, y + 4 + index * LINE_HEIGHT, ACCENT_COLOR)
                            }
                            if (hover) tooltip = listOf(Text.translatable(if (row.expanded) MinecraftHistoryScreenKeys.STATE_WALL_COLLAPSE_TOOLTIP else MinecraftHistoryScreenKeys.STATE_WALL_EXPAND_TOOLTIP))
                            if (row.expanded) {
                                var nestedY = y + row.headerHeight + 8
                                row.groups.forEachIndexed { index, nested ->
                                    val sectionLabel = if (index == 0) {
                                        Text.translatable(MinecraftHistoryScreenKeys.STATE_WALL, row.wallCount)
                                    } else {
                                        Text.translatable(MinecraftHistoryScreenKeys.STATE_RESERVED, row.reservedCount)
                                    }
                                    context.drawTextWithShadow(textRenderer, sectionLabel, layout.left + 16, nestedY, MUTED_COLOR)
                                    nestedY += LINE_HEIGHT
                                    val nestedHover = !dragging && layout.containsCard(mouseX.toDouble(), mouseY.toDouble(), nestedY, nested.height)
                                    val hit = groups.render(context, nested.layout, state.tileCatalog, layout.left + 16, nestedY + 4, mouseX, mouseY, nested.taken, nested.markers)
                                    if (nestedHover) tooltip = hit?.plus(nested.hint?.let(::listOf).orEmpty())
                                    nestedY += nested.height
                                }
                            }
                        }
                        is Row.Space -> Unit
                    }
                }
                y += row.height
            }
        }
        context.disableScissor()
        val bar = layout.scrollbar(contentHeight, scroll)
        val track = layout.scrollbarBounds()
        if (state != null && bar.maximumScroll > 0) {
            context.fill(track.x, track.y, track.x + track.width, track.y + track.height, 0x80505050.toInt())
            context.fill(track.x, bar.thumbTop, track.x + track.width, bar.thumbTop + bar.thumbHeight, 0xffd0d0d0.toInt())
        }
        val middle = layout.footerBounds(width)[2]
        val position = round?.requestedPosition ?: HistoryRoundPositionDto.Initial
        val positionText = when (position) {
            HistoryRoundPositionDto.Initial -> Text.translatable(MinecraftHistoryScreenKeys.STATE_INITIAL)
            is HistoryRoundPositionDto.AfterTransaction -> Text.translatable(MinecraftHistoryScreenKeys.STATE_AFTER_TRANSACTION, position.index + 1)
        }
        val scale = (middle.width.toFloat() / textRenderer.getWidth(positionText).coerceAtLeast(1)).coerceAtMost(1f)
        context.matrices.push()
        context.matrices.translate(
            (middle.x + middle.width / 2).toDouble(),
            (middle.y + middle.height / 2).toDouble(),
            0.0,
        )
        context.matrices.scale(scale, scale, 1f)
        context.drawCenteredTextWithShadow(textRenderer, positionText, 0, -textRenderer.fontHeight / 2, MUTED_COLOR)
        context.matrices.pop()
        if (mouseX >= middle.x && mouseX < middle.x + middle.width && mouseY >= middle.y && mouseY < middle.y + middle.height) {
            tooltip = listOf(positionText)
        }
        super.render(context, mouseX, mouseY, delta)
        if (!dragging) tooltip?.let { context.drawTooltip(textRenderer, it, mouseX, mouseY) }
    }

    /**
     * 建立文字與牌面共同使用的測量列。
     * @param state 已驗證桌況。
     * @return 依區域順序排列的內容列。
     */
    private fun rows(state: HistoryRoundStateDto): List<Row> = buildList {
        val maxWidth = (layout.contentWidth - 32).coerceAtLeast(1).toFloat()

        /** 加入可換行文字。
         * @param text 文字。
         * @param color 文字色。
         * @param portrait 可選玩家頭像。
         */
        fun line(text: Text, color: Int = TEXT_COLOR, portrait: HistoryParticipantSummaryDto? = null) {
            val indent = if (portrait == null) 8 else 22
            textRenderer.wrapLines(text, (layout.contentWidth - indent - 8).coerceAtLeast(1)).forEachIndexed { index, ordered ->
                add(Row.Line(ordered, text, color, indent, portrait.takeIf { index == 0 }))
            }
        }

        /** 加入一組已測量牌面。
         * @param group 牌面幾何。
         * @param hint 可選額外提示。
         */
        fun group(group: HistoryTileGroupLayout, hint: Text? = null) {
            if (group.placements.isEmpty()) line(Text.translatable(MinecraftHistoryScreenKeys.STATE_EMPTY), MUTED_COLOR) else add(Row.Group(group, hint))
        }
        line(Text.translatable(MinecraftHistoryScreenKeys.ROUND_NUMBER, state.roundNumber), ACCENT_COLOR)
        add(Row.Space(8))
        state.outcome?.winnerDetails?.forEach { winner ->
            line(Text.translatable(MinecraftHistoryScreenKeys.ROUND_SCORE, name(state, winner.seatIndex), state.players.firstOrNull { it.initialSeatIndex == winner.seatIndex }?.score ?: "—"), ACCENT_COLOR, participant(state, winner.seatIndex))
            line(Text.translatable(MinecraftHistoryScreenKeys.STATE_WINNING_HAND), ACCENT_COLOR)
            val winning = HistoryRoundStatePresenter.winningHand(state, winner, maxWidth)
            if (winning == null) line(Text.translatable(MinecraftHistoryScreenKeys.STATE_WINNING_HAND_UNAVAILABLE), MUTED_COLOR) else group(winning)
            winner.detailFields.forEach detailField@{ field ->
                val value = field.value
                if (value is HistoryWinDetailValueDto.Tiles && value.tiles.isEmpty()) return@detailField
                events.detailLabel(session.controller.state.value.summary?.detail?.summary?.ruleId, field.id, session.settlementTemplates)?.let { line(it, MUTED_COLOR) }
                events.detailText(field.id, value, session.settlementTemplates).forEach { line(it) }
                if (value is HistoryWinDetailValueDto.Tiles) group(HistoryTileGroupLayoutCalculator.hand(value.tiles, maxWidth = maxWidth))
            }
            add(Row.Space(12))
        }
        state.players.sortedBy { it.initialSeatIndex }.forEach { player ->
            val playerRows = buildPlayerRows(state, player, maxWidth)
            add(Row.PlayerCard(player.initialSeatIndex, playerRows, playerRows.sumOf(Row::height) + 16))
            add(Row.Space(8))
        }
        val wallGroups = listOf(
            Row.Group(HistoryTileGroupLayoutCalculator.hand(state.wallTiles, maxWidth = maxWidth)),
            Row.Group(HistoryTileGroupLayoutCalculator.hand(state.reservedTiles, maxWidth = maxWidth)),
        )
        val wallLabel = Text.translatable(MinecraftHistoryScreenKeys.STATE_WALL_SECTION, state.wallTiles.size, state.reservedTiles.size)
        val wallHeaderHeight = textRenderer.wrapLines(wallLabel, (layout.contentWidth - 40).coerceAtLeast(1)).size * LINE_HEIGHT
        add(Row.WallSection(wallExpanded, state.wallTiles.size, state.reservedTiles.size, wallGroups, wallHeaderHeight))
    }

    /**
     * 建立單一玩家卡片，讓頭像、標頭與牌面共用同一背景。
     * @param state 已驗證桌況。
     * @param player 本卡片對應的玩家。
     * @param maxWidth 卡片內牌組可用寬度。
     * @return 包含標頭、間距與牌組的測量列。
     */
    private fun buildPlayerRows(state: HistoryRoundStateDto, player: HistoryReplayPlayerStateDto, maxWidth: Float): List<Row> = buildList {
        /**
         * 加入卡片內可換行文字。
         * @param text 完整文字。
         * @param color 文字色。
         * @param portrait 首列顯示的玩家頭像。
         * @param fullText 滑鼠指向時顯示的完整提示。
         */
        fun line(
            text: Text,
            color: Int = TEXT_COLOR,
            portrait: HistoryParticipantSummaryDto? = null,
            fullText: Text = text,
        ) {
            val indent = if (portrait == null) 8 else 22
            textRenderer.wrapLines(text, (layout.contentWidth - indent - 16).coerceAtLeast(1)).forEachIndexed { index, ordered ->
                add(Row.Line(ordered, fullText, color, indent, portrait.takeIf { index == 0 }))
            }
        }

        /**
         * 加入卡片內牌組或空區域說明。
         * @param group 已測量牌組。
         * @param hint 牌組額外提示。
         */
        fun group(group: HistoryTileGroupLayout, hint: Text? = null) {
            if (group.placements.isEmpty()) line(Text.translatable(MinecraftHistoryScreenKeys.STATE_EMPTY), MUTED_COLOR) else add(Row.Group(group, hint))
        }

        val heading = Text.translatable(
            MinecraftHistoryScreenKeys.ROUND_SCORE,
            name(state, player.initialSeatIndex).copy().styled { it.withColor(TEXT_COLOR) },
            Text.literal(player.score.toString()).styled { it.withColor(ACCENT_COLOR) },
        )
        state.outcome?.scoreChangesBySeat?.get(player.initialSeatIndex)?.let { change ->
            heading.append(" (").append(events.scoreChangeText(change)).append(")")
        }
        if (player.initialSeatIndex == state.currentPlayerSeat) heading.append(Text.literal("  •  ").styled { it.withColor(MUTED_COLOR) }).append(Text.translatable(MinecraftHistoryScreenKeys.STATE_CURRENT).styled { it.withColor(CURRENT_COLOR) })
        if (player.initialSeatIndex == state.dealerSeat) heading.append(Text.literal("  •  ").styled { it.withColor(MUTED_COLOR) }).append(Text.translatable(MinecraftHistoryScreenKeys.STATE_DEALER).styled { it.withColor(DEALER_COLOR) })
        line(heading, TEXT_COLOR, participant(state, player.initialSeatIndex))
        add(Row.Space(5))
        val canSortHand = session.canSortHand(state)
        val handSorted = canSortHand && session.isHandSorted(state, player.initialSeatIndex)
        val currentMode = Text.translatable(
            if (handSorted) MinecraftHistoryScreenKeys.STATE_SORT_SORTED else MinecraftHistoryScreenKeys.STATE_SORT_ORIGINAL,
        )
        val targetMode = Text.translatable(
            if (handSorted) MinecraftHistoryScreenKeys.STATE_SORT_MODE_ORIGINAL else MinecraftHistoryScreenKeys.STATE_SORT_MODE_SORTED,
        )
        val handHeading = Text.translatable(MinecraftHistoryScreenKeys.STATE_HAND).append(" ·")
        add(
            Row.HandHeading(
                label = handHeading,
                action = currentMode,
                tooltip = if (canSortHand) {
                    Text.translatable(MinecraftHistoryScreenKeys.STATE_SORT_TOOLTIP, targetMode)
                } else {
                    Text.translatable(MinecraftHistoryScreenKeys.STATE_SORT_UNAVAILABLE)
                },
                color = MUTED_COLOR,
                indent = 8,
                actionEnabled = canSortHand,
            ),
        )
        val orderedTiles = session.sortedHandTiles(state, player)
        group(HistoryTileGroupLayoutCalculator.hand(orderedTiles, player.lastDrawn, maxWidth))
        if (player.melds.isNotEmpty()) {
            line(Text.translatable(MinecraftHistoryScreenKeys.STATE_MELDS), MUTED_COLOR)
            group(HistoryRoundStatePresenter.combine(player.melds.map { HistoryTileGroupLayoutCalculator.meld(it, maxWidth) }, maxWidth))
        }
        if (player.setAsideTiles.isNotEmpty()) {
            line(Text.translatable(MinecraftHistoryScreenKeys.STATE_SET_ASIDE), MUTED_COLOR)
            group(HistoryTileGroupLayoutCalculator.hand(player.setAsideTiles, maxWidth = maxWidth))
        }
        line(
            Text.translatable(MinecraftHistoryScreenKeys.STATE_DISCARDS_COUNT, player.discards.size),
            MUTED_COLOR,
            fullText = Text.translatable(MinecraftHistoryScreenKeys.STATE_DISCARDS_COUNT_TOOLTIP, player.discards.size),
        )
        val river = HistoryRoundStatePresenter.discards(player, maxWidth, session.discardMarkers)
        if (river.placements.isEmpty()) group(river) else add(Row.Group(river, taken = player.discards.filter { it.isTaken }.map { it.tile }.toSet(), markers = player.discards.associate { it.tile to it.markers }))
        return@buildList
    }

    /**
     * 以歷史身分取得玩家摘要。
     * @param state 歷史桌況。
     * @param seat 初始座位。
     * @return 對應玩家摘要，或 null。
     */
    private fun participant(state: HistoryRoundStateDto, seat: Int): HistoryParticipantSummaryDto? {
        val id = state.identity.players.firstOrNull { it.initialSeatIndex == seat }?.playerId ?: return null
        return session.controller.state.value.summary?.detail?.summary?.participants?.firstOrNull { it.playerId.equals(id, ignoreCase = true) }
    }

    /**
     * 使用共用名稱來源與具名座位 fallback。
     * @param state 歷史桌況。
     * @param seat 初始座位。
     * @return 玩家名稱。
     */
    private fun name(state: HistoryRoundStateDto, seat: Int): Text = participant(state, seat)?.let {
        session.participants.name(it, session.controller.state.value.summary?.detail?.summary?.participants.orEmpty())
    } ?: Text.translatable(MinecraftHistoryScreenKeys.ROUND_SEAT, seat + 1)

    /** 目前選取的局。 */
    private fun currentRound(): HistoryBrowseRoundState? = session.controller.state.value.let { it.selectedRoundNumber?.let(it.rounds::get) }

    /** 已成功桌況的內容高度。 */
    private fun readyHeight(): Int = currentRound()?.takeIf { it.stateStatus == HistoryBrowseStatus.Ready }?.state?.let { rows(it).sumOf(Row::height) + 8 } ?: layout.viewportHeight

    /**
     * 內容區滾輪輸入，端點限制避免抖動。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param amount 捲動量。
     * @return 是否處理。
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (mouseY < layout.contentTop || mouseY >= layout.contentBottom) return super.mouseScrolled(mouseX, mouseY, amount)
        scroll = layout.clampScroll(scroll - amount * SCROLL_STEP, readyHeight())
        session.controller.rememberRoundPosition(scroll)
        return true
    }

    /**
     * 處理捲軸抓取並保留滑塊內偏移。
     * @param mouseX 水平座標。
     * @param mouseY 垂直座標。
     * @param button 按鍵。
     * @return 是否處理。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val track = layout.scrollbarBounds()
        if (button == 0 && currentRound()?.stateStatus == HistoryBrowseStatus.Ready && layout.maximumScroll(readyHeight()) > 0 && mouseX >= track.x && mouseX < track.x + track.width && mouseY >= track.y && mouseY < track.y + track.height) {
            dragging = true
            grabOffset = layout.grabOffset(readyHeight(), scroll, mouseY)
            updateDrag(mouseY)
            return true
        }
        if (button == 0 && currentRound()?.stateStatus == HistoryBrowseStatus.Ready) {
            val state = currentRound()?.state
            if (state != null) {
                var y = layout.contentTop + 4 - scroll.toInt()
                rows(state).forEach { row ->
                    when (row) {
                        is Row.PlayerCard -> {
                            var nestedY = y + 8
                            row.rows.forEach { nested ->
                                if (nested is Row.HandHeading &&
                                    nested.actionEnabled &&
                                    layout.containsCard(mouseX, mouseY, nestedY, nested.height) &&
                                    mouseX >= layout.left + nested.indent + 8 + textRenderer.getWidth(nested.label) + 4 &&
                                    mouseX < layout.left + nested.indent + 8 + textRenderer.getWidth(nested.label) + 4 + textRenderer.getWidth(nested.action) &&
                                    mouseY >= nestedY &&
                                    mouseY < nestedY + nested.height
                                ) {
                                    session.toggleHandSorting(state, row.seat)
                                    return true
                                }
                                nestedY += nested.height
                            }
                        }
                        is Row.WallSection -> if (layout.containsCard(mouseX, mouseY, y, row.headerHeight + 8)) {
                            wallExpanded = !wallExpanded
                            return true
                        }
                        else -> Unit
                    }
                    y += row.height
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /**
     * 套用捲軸拖曳位置。
     * @param mouseY 游標垂直座標。
     */
    private fun updateDrag(mouseY: Double) {
        scroll = layout.scrollbar(readyHeight(), scroll).scrollIndexFor(mouseY, grabOffset).toDouble()
        session.controller.rememberRoundPosition(scroll)
    }

    /**
     * 拖曳不穿透其他控制項。
     * @param mouseX 水平座標。
     * @param mouseY 垂直座標。
     * @param button 按鍵。
     * @param deltaX 水平位移。
     * @param deltaY 垂直位移。
     * @return 是否處理。
     */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = if (dragging && button == 0) {
        updateDrag(mouseY)
        true
    } else {
        super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    /**
     * 結束拖曳。
     * @param mouseX 水平座標。
     * @param mouseY 垂直座標。
     * @param button 按鍵。
     * @return 是否處理。
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = dragging && button == 0
        dragging = false
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    /** 共用測量列。 */
    private sealed interface Row {
        /** 列高。 */
        val height: Int

        /** 文字列。
         * @property text 已換行文字。
         * @property fullText 完整提示。
         * @property color 文字色。
         * @property indent 左側縮排。
         * @property participant 可選頭像。
         */
        data class Line(
            val text: OrderedText,
            val fullText: Text,
            val color: Int,
            val indent: Int,
            val participant: HistoryParticipantSummaryDto?,
        ) : Row {
            /** 固定文字列高。 */
            override val height: Int = LINE_HEIGHT
        }

        /** 牌面列。
         * @property layout 牌面幾何。
         * @property hint 額外提示。
         * @property taken 已被取走的牌索引，保持空槽。
         * @property markers 各牌公開標記。
         */
        data class Group(
            val layout: HistoryTileGroupLayout,
            val hint: Text? = null,
            val taken: Set<Int> = emptySet(),
            val markers: Map<Int, Set<String>> = emptyMap(),
        ) : Row {
            /** 牌組高度與垂直內距。 */
            override val height: Int = ceil(layout.height).toInt() + 8
        }

        /** 單一玩家的完整牌面卡片。
         * @property seat 玩家初始座位，用於個別保存手牌排序狀態。
         * @property rows 玩家標頭、手牌、副露與牌河列。
         * @property height 卡片總高度。
         */
        data class PlayerCard(
            val seat: Int,
            val rows: List<Row>,
            override val height: Int,
        ) : Row

        /** 可單獨切換一位玩家手牌排序的標題列。
         * @property label 顯示中的手牌標題。
         * @property action 顯示目前排列方式的可點擊文字。
         * @property tooltip 點擊後的目標排序模式說明。
         * @property color 文字色。
         * @property indent 左側縮排。
         * @property actionEnabled 是否允許切換排序。
         */
        data class HandHeading(
            val label: Text,
            val action: Text,
            val tooltip: Text,
            val color: Int,
            val indent: Int,
            val actionEnabled: Boolean,
        ) : Row {
            /** 固定文字列高。 */
            override val height: Int = LINE_HEIGHT
        }

        /** 可展開的活牌與保留牌區塊。
         * @property expanded 是否顯示兩區牌面。
         * @property wallCount 活牌數量。
         * @property reservedCount 保留牌數量。
         * @property groups 展開後的牌面群組。
         * @property headerHeight 換行後標題文字的實際高度。
         */
        data class WallSection(
            val expanded: Boolean,
            val wallCount: Int,
            val reservedCount: Int,
            val groups: List<Group>,
            val headerHeight: Int,
        ) : Row {
            /** 標頭及展開後牌面所需高度。 */
            override val height: Int = headerHeight + 12 + if (expanded) groups.sumOf { it.height + LINE_HEIGHT } else 0
        }

        /** 段落間距。
         * @property height 間距高度。
         */
        data class Space(override val height: Int) : Row
    }

    /** 共用尺寸與配色。 */
    private companion object {
        /** 文字列高。 */
        const val LINE_HEIGHT = 11

        /** 滾輪步幅。 */
        const val SCROLL_STEP = 24

        /** 一般文字。 */
        const val TEXT_COLOR = 0xffffff

        /** 次要文字。 */
        const val MUTED_COLOR = 0xaaaaaa

        /** 強調文字。 */
        const val ACCENT_COLOR = 0x8ed5df

        /** 目前回合玩家標記的柔和綠色。 */
        const val CURRENT_COLOR = 0x9bd49b

        /** 莊家標記的暖金色。 */
        const val DEALER_COLOR = 0xe8c878

        /** 手牌排序切換文字。 */
        const val SORT_COLOR = 0xbcbcbc

        /** 指向手牌排序切換文字時的顏色。 */
        const val SORT_HOVER_COLOR = 0x8ed5df

        /** 牌組底色。 */
        val CARD_COLOR = 0xB0202020.toInt()

        /** 指向牌組底色。 */
        val HOVER_COLOR = 0xB0343434.toInt()
    }
}
