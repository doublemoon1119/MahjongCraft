package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailValueDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.CycleButtonInput
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelIcon
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelIcons
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import kotlin.math.ceil

/**
 * 顯示初始或交易後的唯讀牌面，以及該筆結算保存的完整和牌手牌。
 *
 * @property session 共用查詢、導航及呈現來源。
 */
internal class HistoryRoundStateScreen(
    private val session: HistoryBrowseSession,
) : Screen(Component.translatable(MinecraftHistoryScreenKeys.STATE_TITLE)) {
    /** 固定區、內容及捲軸幾何。 */
    private var layout = HistoryRoundEventsLayout.measure(width = 0, height = 0, contentTop = 0)

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

    /** 上一個畫面游標指向的牌；被鳴的牌與被鳴走的空位依此一起蓋上強調色層。 */
    private var linkedTile: Int? = null

    /** 前後及重試控制項。 */
    private val queryButtons = mutableListOf<Button>()

    /** 各玩家手牌順序的切換按鈕，依開局座位建立；隨內容捲動，由本畫面在內容裁切範圍內繪製。 */
    private val sortButtons = mutableMapOf<Int, RestartableMarqueeButtonWidget>()

    /** 只在牌面讀取失敗時顯示的重試控制項。 */
    private lateinit var retryButton: Button

    /** 共用牌面 renderer。 */
    private val groups = HistoryTileGroupRenderer(
        faces = session.tileFaces,
        assets = session.tileAssets,
        markerDisplays = session.discardMarkers,
        displayNames = session.tileDisplayNames,
        networkRegistries = session.networkRegistries,
    )

    /** 共用規則欄位標題與文字呈現。 */
    private val events = session.eventPresenter

    /** 建立固定控制項，恢復同一狀態的捲動位置。 */
    override fun init() {
        layout = HistoryRoundEventsLayout.measure(width = width, height = height, contentTop = FormScreenLayout.TITLE_BOTTOM)
        scroll = currentRound()?.stateScrollOffset ?: 0.0
        displayedPosition = currentRound()?.confirmedPosition
        queryButtons.clear()
        sortButtons.clear()
        clearWidgets()
        val previous = layout.footerSlot(0)
        queryButtons += addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.PREVIOUS)) {
                if (HistoryPaging.jumpsToEnd()) session.controller.firstRoundState() else session.controller.previousRoundState()
            }
                .dimensions(previous.x, previous.y, previous.width, previous.height).build(),
        )
        val next = layout.footerSlot(2)
        queryButtons += addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.NEXT)) {
                if (HistoryPaging.jumpsToEnd()) session.controller.lastRoundState() else session.controller.nextRoundState()
            }
                .dimensions(next.x, next.y, next.width, next.height).build(),
        )
        val back = layout.footerSlot(HistoryPageFrame.LAST_SLOT)
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.BACK)) { session.backToRound() }
                .dimensions(back.x, back.y, back.width, back.height).build(),
        )
        retryButton = RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.RETRY)) { session.controller.retry() }
            .dimensions(0, 0, 1, 20).build()
        retryButton.visible = false
        addRenderableWidget(retryButton)
    }

    /**
     * 取得玩家手牌順序的切換按鈕；同一位玩家重複使用同一顆按鈕。
     *
     * @param seat 開局座位。
     * @return 按鈕；文字、位置與提示由繪製時決定。
     */
    private fun sortButton(seat: Int): RestartableMarqueeButtonWidget = sortButtons.getOrPut(seat) {
        addWidget(
            RestartableMarqueeButtonWidget.builder(Component.empty()) {
                currentRound()?.state?.let { session.toggleHandSorting(it, seat) }
            }.dimensions(0, 0, 1, MINI_BUTTON_SIZE).build().also {
                it.visible = false
                it.clickClip = { x, y -> layout.containsCard(x, y, layout.contentTop, layout.viewportHeight) }
            },
        )
    }

    /** 歷史瀏覽不暫停整合伺服器。 */
    override fun isPauseScreen(): Boolean = false

    /** 返回目前局事件，保留該頁位置。 */
    override fun onClose() {
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
     *
     * @param context 繪製上下文。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param delta 幀內插值。
     */
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val round = currentRound()
        val status = round?.stateStatus ?: HistoryBrowseStatus.Failed(HistoryBrowseFailure.NOT_AVAILABLE)
        val state = round?.state?.takeIf { status == HistoryBrowseStatus.Ready && it.position == round.confirmedPosition }
        val heading = title.copy().append(TITLE_SEPARATOR).append(Component.translatable(MinecraftHistoryScreenKeys.ROUND_NUMBER, round?.roundNumber ?: "—"))
        ScreenRendering.title(context, font, heading, width)
        val canQuery = session.controller.canQueryRound()
        queryButtons[0].active = canQuery && state != null && state.position != HistoryRoundPositionDto.Initial
        queryButtons[1].active = canQuery &&
            state != null &&
            when (val position = state.position) {
                HistoryRoundPositionDto.Initial -> round.hasTransaction(0)
                is HistoryRoundPositionDto.AfterTransaction -> round.hasTransaction(position.index + 1)
            }
        retryButton.visible = false
        retryButton.active = false
        val queryStatus = when {
            session.controller.isRefreshCoolingDown() -> Component.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP)
            !canQuery -> Component.translatable(MinecraftHistoryScreenKeys.QUERY_LOADING_TOOLTIP)
            else -> null
        }
        queryButtons[0].tooltip = Tooltip.create(HistoryPaging.buttonTooltip(MinecraftHistoryScreenKeys.HINT_PREVIOUS_TRANSACTION, MinecraftHistoryScreenKeys.HINT_INITIAL_STATE, queryStatus))
        queryButtons[1].tooltip = Tooltip.create(HistoryPaging.buttonTooltip(MinecraftHistoryScreenKeys.HINT_NEXT_TRANSACTION, MinecraftHistoryScreenKeys.HINT_LAST_TRANSACTION, queryStatus))
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
        var tooltip: List<Component>? = null
        var hoveredTile: Int? = null
        sortButtons.values.forEach { it.visible = false }
        val pointerInside = !dragging && mouseX >= layout.left && mouseX < layout.right && mouseY >= layout.contentTop && mouseY < layout.contentBottom
        val pointerX = if (pointerInside) mouseX else OFFSCREEN_POINTER
        val pointerY = if (pointerInside) mouseY else OFFSCREEN_POINTER
        context.enableScissor(layout.left, layout.contentTop, layout.right, layout.contentBottom)
        if (state == null) {
            val message = if (status is HistoryBrowseStatus.Failed) Component.translatable(status.reason.translationKey) else Component.translatable(MinecraftHistoryScreenKeys.LOADING)
            val lines = font.split(message, (layout.contentWidth - 16).coerceAtLeast(1)).take(
                n = ((layout.contentBottom - layout.contentTop - 32) / LINE_HEIGHT).coerceAtLeast(1),
            )
            lines.forEachIndexed { index, line ->
                context.drawString(font, line, layout.left + 8, layout.contentTop + 8 + index * LINE_HEIGHT, TEXT_COLOR)
            }
            if (status is HistoryBrowseStatus.Failed) {
                val retryWidth = 80.coerceAtMost((layout.contentWidth - 16).coerceAtLeast(1))
                val retryX = layout.right - retryWidth
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
                            row.participant?.let { session.participants.render(it, context, layout.left + 8, y, 10, matchId = session.controller.state.value.summary?.detail?.summary?.matchId) }
                            context.drawString(font, row.text, layout.left + row.indent, y, row.color)
                            if (hover) tooltip = listOf(row.fullText)
                        }
                        is Row.Group -> {
                            context.fill(layout.left, y, layout.right, y + row.height - 4, if (hover) HOVER_COLOR else CARD_COLOR)
                            val hit = groups.render(context, row.layout, state.tileCatalog, layout.left + 8, y + 4, mouseX, mouseY, row.taken, row.markers, row.notes, linkedTile, session.tileAppearance(state.identity.matchId))
                            if (hover) hit?.let { hoveredTile = it.tile }
                            if (hover) tooltip = hit?.tooltip?.plus(row.hint?.let(::listOf).orEmpty())
                        }
                        is Row.HandHeading -> Unit
                        is Row.PlayerCard -> {
                            context.fill(layout.left, y, layout.right, y + row.height - 4, if (hover) HOVER_COLOR else CARD_COLOR)
                            row.scoreChange?.takeIf { it != 0 }?.let { change ->
                                val borderColor = OPAQUE or events.scoreChangeColor(change)
                                val bottom = y + row.height - 4
                                context.fill(layout.left, y, layout.right, y + 1, borderColor)
                                context.fill(layout.left, bottom - 1, layout.right, bottom, borderColor)
                                context.fill(layout.left, y, layout.left + 1, bottom, borderColor)
                                context.fill(layout.right - 1, y, layout.right, bottom, borderColor)
                            }
                            var nestedY = y + 8
                            row.rows.forEach { nested ->
                                val nestedHover = !dragging && layout.containsCard(mouseX.toDouble(), mouseY.toDouble(), nestedY, nested.height)
                                when (nested) {
                                    is Row.Line -> {
                                        nested.participant?.let { session.participants.render(it, context, layout.left + 16, nestedY, 10, matchId = session.controller.state.value.summary?.detail?.summary?.matchId) }
                                        context.drawString(font, nested.text, layout.left + nested.indent + 8, nestedY, nested.color)
                                        if (nestedHover) tooltip = listOf(nested.fullText)
                                    }
                                    is Row.HandHeading -> {
                                        val textX = layout.left + nested.inset
                                        val textY = nestedY + (nested.height - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2
                                        nested.label?.let { context.drawString(font, it, textX, textY, MUTED_COLOR) }
                                        val button = sortButton(nested.seat)
                                        button.message = nested.action
                                        button.width = nested.buttonWidth
                                        button.x = textX + nested.buttonOffset
                                        button.y = nestedY + (nested.height - button.height) / 2
                                        button.active = nested.actionEnabled
                                        button.tooltip = Tooltip.create(nested.tooltip)
                                        button.visible = button.y + button.height > layout.contentTop && button.y < layout.contentBottom
                                        button.render(context, pointerX, pointerY, delta)
                                    }
                                    is Row.Group -> {
                                        val hit = groups.render(context, nested.layout, state.tileCatalog, layout.left + 16, nestedY + 4, mouseX, mouseY, nested.taken, nested.markers, nested.notes, linkedTile, session.tileAppearance(state.identity.matchId))
                                        if (nestedHover) hit?.let { hoveredTile = it.tile }
                                        if (nestedHover) tooltip = hit?.tooltip?.plus(nested.hint?.let(::listOf).orEmpty())
                                    }
                                    is Row.Space, is Row.PlayerCard, is Row.WallSection -> Unit
                                }
                                nestedY += nested.height
                            }
                        }
                        is Row.WallSection -> {
                            context.fill(layout.left, y, layout.right, y + row.height - 4, if (hover) HOVER_COLOR else CARD_COLOR)
                            val label = Component.translatable(MinecraftHistoryScreenKeys.STATE_WALL_SECTION, row.wallCount, row.reservedCount)
                            if (row.expanded) {
                                PixelIcons.arrow(context = context, x = layout.left + 8, y = y + 6, pointsUp = false, color = ACCENT_COLOR)
                            } else {
                                PixelIcons.icon(context = context, icon = PixelIcon.OPEN_RIGHT, x = layout.left + 9, y = y + 4, color = ACCENT_COLOR)
                            }
                            font.split(label, (layout.contentWidth - 40).coerceAtLeast(1)).forEachIndexed { index, line ->
                                context.drawString(font, line, layout.left + 22, y + 4 + index * LINE_HEIGHT, ACCENT_COLOR)
                            }
                            if (hover) tooltip = listOf(Component.translatable(if (row.expanded) MinecraftHistoryScreenKeys.STATE_WALL_COLLAPSE_TOOLTIP else MinecraftHistoryScreenKeys.STATE_WALL_EXPAND_TOOLTIP))
                            if (row.expanded) {
                                var nestedY = y + row.headerHeight + 8
                                row.groups.forEachIndexed { index, nested ->
                                    val sectionLabel = if (index == 0) {
                                        Component.translatable(MinecraftHistoryScreenKeys.STATE_WALL, row.wallCount)
                                    } else {
                                        Component.translatable(MinecraftHistoryScreenKeys.STATE_RESERVED, row.reservedCount)
                                    }
                                    context.drawString(font, sectionLabel, layout.left + 16, nestedY, MUTED_COLOR)
                                    nestedY += LINE_HEIGHT
                                    val nestedHover = !dragging && layout.containsCard(mouseX.toDouble(), mouseY.toDouble(), nestedY, nested.height)
                                    val hit = groups.render(context, nested.layout, state.tileCatalog, layout.left + 16, nestedY + 4, mouseX, mouseY, nested.taken, nested.markers, nested.notes, linkedTile, session.tileAppearance(state.identity.matchId))
                                    if (nestedHover) hit?.let { hoveredTile = it.tile }
                                    if (nestedHover) tooltip = hit?.tooltip?.plus(nested.hint?.let(::listOf).orEmpty())
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
        // 兩端可能分屬先後畫出的牌組，強調色層沿用上一個畫面的指向結果。
        linkedTile = hoveredTile
        val bar = layout.scrollbar(contentHeight, scroll)
        if (state != null && bar.maximumScroll > 0) {
            VerticalScrollbar.render(context, layout.scrollbarBounds().x, bar)
        }
        val middle = layout.footerSlot(1)
        val position = round?.requestedPosition ?: HistoryRoundPositionDto.Initial
        val positionText = when (position) {
            HistoryRoundPositionDto.Initial -> Component.translatable(MinecraftHistoryScreenKeys.STATE_INITIAL)
            is HistoryRoundPositionDto.AfterTransaction -> Component.translatable(MinecraftHistoryScreenKeys.STATE_AFTER_TRANSACTION, position.index + 1)
        }
        HistoryPaging.renderRange(
            context = context,
            textRenderer = font,
            bounds = middle,
            primary = positionText,
            secondary = HistoryPaging.totalText(round?.transactionCount),
        )
        super.render(context, mouseX, mouseY, delta)
        if (!dragging) tooltip?.let { context.renderComponentTooltip(font, it, mouseX, mouseY) }
    }

    /**
     * 建立文字與牌面共同使用的測量列。
     *
     * @param state 已驗證桌況。
     * @return 依區域順序排列的內容列。
     */
    private fun rows(state: HistoryRoundStateDto): List<Row> = buildList {
        val maxWidth = (layout.contentWidth - 32).coerceAtLeast(1).toFloat()

        state.players.sortedBy { it.initialSeatIndex }.forEach { player ->
            val playerRows = buildPlayerRows(state, player, maxWidth)
            val scoreChange = state.outcome?.scoreChangesBySeat?.get(player.initialSeatIndex)
            add(Row.PlayerCard(player.initialSeatIndex, playerRows, playerRows.sumOf(Row::height) + 16, scoreChange))
            add(Row.Space(8))
        }
        val wallGroups = if (wallExpanded) {
            listOf(
                Row.Group(HistoryTileGroupLayoutCalculator.hand(state.wallTiles, maxWidth = maxWidth)),
                Row.Group(HistoryTileGroupLayoutCalculator.hand(state.reservedTiles, maxWidth = maxWidth)),
            )
        } else {
            emptyList()
        }
        val wallLabel = Component.translatable(MinecraftHistoryScreenKeys.STATE_WALL_SECTION, state.wallTiles.size, state.reservedTiles.size)
        val wallHeaderHeight = font.split(wallLabel, (layout.contentWidth - 40).coerceAtLeast(1)).size * LINE_HEIGHT
        add(Row.WallSection(wallExpanded, state.wallTiles.size, state.reservedTiles.size, wallGroups, wallHeaderHeight))
    }

    /**
     * 建立單一玩家卡片，讓頭像、標頭與牌面共用同一背景。
     *
     * @param state 已驗證桌況。
     * @param player 本卡片對應的玩家。
     * @param maxWidth 卡片內牌組可用寬度。
     * @return 包含標頭、間距與牌組的測量列。
     */
    private fun buildPlayerRows(state: HistoryRoundStateDto, player: HistoryReplayPlayerStateDto, maxWidth: Float): List<Row> = buildList {
        /**
         * 加入卡片內可換行文字。
         *
         * @param text 完整文字。
         * @param color 文字色。
         * @param portrait 首列顯示的玩家頭像。
         * @param fullText 滑鼠指向時顯示的完整提示。
         */
        fun line(
            text: Component,
            color: Int = TEXT_COLOR,
            portrait: HistoryParticipantSummaryDto? = null,
            fullText: Component = text,
        ) {
            val indent = if (portrait == null) 8 else 22
            font.split(text, (layout.contentWidth - indent - 16).coerceAtLeast(1)).forEachIndexed { index, ordered ->
                add(Row.Line(ordered, fullText, color, indent, portrait.takeIf { index == 0 }))
            }
        }

        /**
         * 加入卡片內牌組或空區域說明。
         *
         * @param group 已測量牌組。
         * @param hint 牌組額外提示。
         */
        fun group(group: HistoryTileGroupLayout, hint: Component? = null) {
            if (group.placements.isEmpty()) line(Component.translatable(MinecraftHistoryScreenKeys.STATE_EMPTY), MUTED_COLOR) else add(Row.Group(group, hint))
        }

        val heading = Component.translatable(
            MinecraftHistoryScreenKeys.ROUND_SCORE,
            name(state, player.initialSeatIndex).copy().withStyle { it.withColor(TEXT_COLOR) },
            Component.literal(player.score.toString()).withStyle { it.withColor(ACCENT_COLOR) },
        )
        state.outcome?.scoreChangesBySeat?.get(player.initialSeatIndex)?.let { change ->
            heading.append(" (").append(events.scoreChangeText(change)).append(")")
        }
        if (player.initialSeatIndex == state.currentPlayerSeat) heading.append(Component.literal("  •  ").withStyle { it.withColor(MUTED_COLOR) }).append(Component.translatable(MinecraftHistoryScreenKeys.STATE_CURRENT).withStyle { it.withColor(CURRENT_COLOR) })
        if (player.initialSeatIndex == state.dealerSeat) heading.append(Component.literal("  •  ").withStyle { it.withColor(MUTED_COLOR) }).append(Component.translatable(MinecraftHistoryScreenKeys.STATE_DEALER).withStyle { it.withColor(DEALER_COLOR) })
        line(heading, TEXT_COLOR, participant(state, player.initialSeatIndex))
        add(Row.Space(5))
        val canSortHand = session.canSortHand(state)
        val handSorted = canSortHand && session.isHandSorted(state, player.initialSeatIndex)
        val currentMode = Component.translatable(
            if (handSorted) MinecraftHistoryScreenKeys.STATE_SORT_SORTED else MinecraftHistoryScreenKeys.STATE_SORT_ORIGINAL,
        )
        val handHeading = Component.translatable(MinecraftHistoryScreenKeys.STATE_HAND)
        val inset = 16.coerceAtMost((layout.contentWidth - 1) / 2)
        val availableWidth = layout.contentWidth - inset * 2
        val preferredWidth = font.width(currentMode) + MINI_BUTTON_PADDING * 2
        val labelWidth = font.width(handHeading) + 4
        val inline = labelWidth + preferredWidth <= availableWidth
        if (!inline) line(handHeading, MUTED_COLOR)
        val modes = listOf(MinecraftHistoryScreenKeys.STATE_SORT_MODE_ORIGINAL, MinecraftHistoryScreenKeys.STATE_SORT_MODE_SORTED).map { Component.translatable(it) }
        add(
            element = Row.HandHeading(
                seat = player.initialSeatIndex,
                label = handHeading.takeIf { inline },
                action = currentMode,
                tooltip = if (canSortHand) {
                    ControlTooltip.options(
                        description = Component.translatable(MinecraftHistoryScreenKeys.STATE_SORT_DESCRIPTION),
                        labels = modes,
                        currentIndex = if (handSorted) 1 else 0,
                        hints = CycleButtonInput.hintTexts(optionCount = modes.size),
                    )
                } else {
                    ControlTooltip.description(Component.translatable(MinecraftHistoryScreenKeys.STATE_SORT_UNAVAILABLE))
                },
                inset = inset,
                buttonOffset = if (inline) labelWidth else 0,
                buttonWidth = preferredWidth.coerceAtMost(availableWidth),
                actionEnabled = canSortHand,
            ),
        )
        val orderedTiles = session.sortedHandTiles(state, player)
        group(HistoryRoundStatePresenter.cardHand(state, player, orderedTiles, maxWidth))
        HistoryRoundStatePresenter.exposedTiles(state, player, maxWidth)?.let { exposed ->
            line(exposed.heading(session.setAsideDisplays::findAreaName), MUTED_COLOR)
            add(Row.Group(exposed.layout, notes = exposed.notes { seat -> name(state, seat) }))
        }
        state.outcome?.winnerDetails?.firstOrNull { it.seatIndex == player.initialSeatIndex }?.let { winner ->
            line(Component.translatable(MinecraftHistoryScreenKeys.ROUND_WIN_DETAILS), ACCENT_COLOR)
            if (HistoryRoundStatePresenter.winningHand(state, winner, maxWidth) == null) {
                line(Component.translatable(MinecraftHistoryScreenKeys.STATE_WINNING_HAND_UNAVAILABLE), MUTED_COLOR)
            }
            // 牌面欄位只列桌上公開的牌，這一頁的玩家卡片與牌山區已經畫出它們，因此只列文字欄位。
            winner.detailFields.filterNot { it.value is HistoryWinDetailValueDto.TileFaces }.forEach { field ->
                events.detailLabel(session.controller.state.value.summary?.detail?.summary?.ruleId, field.id, session.settlementTemplates)?.let { line(it, MUTED_COLOR) }
                events.detailText(field.id, field.value, session.settlementTemplates).forEach { line(it) }
            }
        }
        line(
            text = Component.translatable(MinecraftHistoryScreenKeys.STATE_DISCARDS_COUNT, player.discards.size),
            color = MUTED_COLOR,
            fullText = Component.translatable(MinecraftHistoryScreenKeys.STATE_DISCARDS_COUNT_TOOLTIP, player.discards.size),
        )
        val river = HistoryRoundStatePresenter.discards(player, maxWidth, session.discardMarkers)
        if (river.placements.isEmpty()) {
            group(river)
        } else {
            val takenBySeat = HistoryRoundStatePresenter.takenBySeat(state)
            val taken = player.discards.filter { it.isTaken }.associate { discard ->
                discard.tile to Component.translatable(MinecraftHistoryScreenKeys.STATE_TAKEN_BY, name(state, takenBySeat.getValue(discard.tile)))
            }
            add(Row.Group(river, taken = taken, markers = player.discards.associate { it.tile to it.markers }))
        }
        return@buildList
    }

    /**
     * 以歷史身分取得玩家摘要。
     *
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
     *
     * @param state 歷史桌況。
     * @param seat 初始座位。
     * @return 玩家名稱。
     */
    private fun name(state: HistoryRoundStateDto, seat: Int): Component = participant(state, seat)?.let {
        session.participants.name(it, session.controller.state.value.summary?.detail?.summary?.participants.orEmpty(), session.controller.state.value.summary?.detail?.summary?.matchId)
    } ?: Component.translatable(MinecraftHistoryScreenKeys.ROUND_SEAT, seat + 1)

    /** 目前選取的局。 */
    private fun currentRound(): HistoryBrowseRoundState? = session.controller.state.value.let { it.selectedRoundNumber?.let(it.rounds::get) }

    /** 已成功桌況的內容高度。 */
    private fun readyHeight(): Int = currentRound()?.takeIf { it.stateStatus == HistoryBrowseStatus.Ready }?.state?.let { rows(it).sumOf(Row::height) + 8 } ?: layout.viewportHeight

    /**
     * 內容區滾輪輸入，端點限制避免抖動。
     *
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
     *
     * @param mouseX 水平座標。
     * @param mouseY 垂直座標。
     * @param button 按鍵。
     * @return 是否處理。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && currentRound()?.stateStatus == HistoryBrowseStatus.Ready && layout.maximumScroll(readyHeight()) > 0 && layout.isOverScrollbar(mouseX, mouseY)) {
            dragging = true
            grabOffset = layout.grabOffset(readyHeight(), scroll, mouseY)
            updateDrag(mouseY)
            return true
        }
        if (button == 0 && currentRound()?.stateStatus == HistoryBrowseStatus.Ready) {
            val state = currentRound()?.state
            val insideContent = mouseX >= layout.left && mouseX < layout.right && mouseY >= layout.contentTop && mouseY < layout.contentBottom
            if (insideContent && sortButtons.values.any { it.visible && it.mouseClicked(mouseX, mouseY, button) }) return true
            if (state != null) {
                var y = layout.contentTop + 4 - scroll.toInt()
                rows(state).forEach { row ->
                    when (row) {
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
     *
     * @param mouseY 游標垂直座標。
     */
    private fun updateDrag(mouseY: Double) {
        scroll = layout.scrollbar(readyHeight(), scroll).scrollIndexFor(mouseY, grabOffset).toDouble()
        session.controller.rememberRoundPosition(scroll)
    }

    /**
     * 拖曳不穿透其他控制項。
     *
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
     *
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

        /**
         * 文字列。
         *
         * @property text 已換行文字。
         * @property fullText 完整提示。
         * @property color 文字色。
         * @property indent 左側縮排。
         * @property participant 可選頭像。
         */
        data class Line(
            val text: FormattedCharSequence,
            val fullText: Component,
            val color: Int,
            val indent: Int,
            val participant: HistoryParticipantSummaryDto?,
        ) : Row {
            /** 固定文字列高。 */
            override val height: Int = LINE_HEIGHT
        }

        /**
         * 牌面列。
         *
         * @property layout 牌面幾何。
         * @property hint 額外提示。
         * @property taken 已被取走的牌索引對應取走它的說明，保持空槽。
         * @property markers 各牌公開標記。
         * @property notes 各牌的額外說明，例如副露被鳴的牌來自哪位玩家。
         */
        data class Group(
            val layout: HistoryTileGroupLayout,
            val hint: Component? = null,
            val taken: Map<Int, Component> = emptyMap(),
            val markers: Map<Int, Set<String>> = emptyMap(),
            val notes: Map<Int, List<HistoryTileNote>> = emptyMap(),
        ) : Row {
            /** 牌組高度與垂直內距。 */
            override val height: Int = ceil(layout.height).toInt() + 8
        }

        /**
         * 單一玩家的完整牌面卡片。
         *
         * @property seat 玩家初始座位，用於個別保存手牌排序狀態。
         * @property rows 玩家標頭、手牌、副露、和牌明細與牌河列。
         * @property height 卡片總高度。
         * @property scoreChange 本局結算的分數變化；增加時加上綠色外框、減少時加上紅色外框，沒有變化或尚未結算時不加。
         */
        data class PlayerCard(
            val seat: Int,
            val rows: List<Row>,
            override val height: Int,
            val scoreChange: Int?,
        ) : Row

        /**
         * 可單獨切換一位玩家手牌排序的標題列。
         *
         * @property seat 玩家座位。
         * @property label 同列顯示的手牌標題；標題移至上一列時為 null。
         * @property action 顯示目前排列方式的可點擊文字。
         * @property tooltip 點擊後的目標排序模式說明。
         * @property inset 內容區到標題列左側的縮排。
         * @property buttonOffset 按鈕相對於標題列左側的位置。
         * @property buttonWidth 按鈕在內容區內的寬度。
         * @property actionEnabled 是否允許切換排序。
         */
        data class HandHeading(
            val seat: Int,
            val label: Component?,
            val action: Component,
            val tooltip: Component,
            val inset: Int,
            val buttonOffset: Int,
            val buttonWidth: Int,
            val actionEnabled: Boolean,
        ) : Row {
            /** 容得下切換按鈕的列高。 */
            override val height: Int = MINI_BUTTON_SIZE + 2
        }

        /**
         * 可展開的活牌與保留牌區塊。
         *
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

        /**
         * 段落間距。
         *
         * @property height 間距高度。
         */
        data class Space(override val height: Int) : Row
    }

    /** 共用尺寸與配色。 */
    private companion object {
        /** 標題與局數之間的分隔。 */
        const val TITLE_SEPARATOR = " · "

        /** 游標不在內容區時交給卡片內按鈕的座標。 */
        const val OFFSCREEN_POINTER = -1

        /** 卡片內小按鈕的高度。 */
        const val MINI_BUTTON_SIZE = 16

        /** 卡片內文字按鈕左右的留白。 */
        const val MINI_BUTTON_PADDING = 6

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

        /** 牌組底色。 */
        const val CARD_COLOR = 0xB0202020.toInt()

        /** 指向牌組底色。 */
        const val HOVER_COLOR = 0xB0343434.toInt()

        /** 將 RGB 配色轉為不透明 ARGB 的 alpha 位元。 */
        const val OPAQUE = 0xFF000000.toInt()
    }
}
