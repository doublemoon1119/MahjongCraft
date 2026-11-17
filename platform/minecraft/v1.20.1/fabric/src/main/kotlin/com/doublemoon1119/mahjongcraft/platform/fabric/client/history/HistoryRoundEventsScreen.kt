package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailValueDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.PixelIcon
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.math.ceil

/**
 * 顯示已授權的單局交易及保存結果，不在客戶端重算規則或計分。
 *
 * @property session 共用導航與查詢生命週期。
 * @property tileFaceRenderer 共用 GUI 牌面 renderer。
 * @property tileAssetRegistry 牌種素材映射來源。
 * @property eventPresenter 已驗證事件的呈現 helper。
 * @property settlementTemplates 明細欄位標題的規則模板來源。
 */
internal class HistoryRoundEventsScreen(
    private val session: HistoryBrowseSession,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val eventPresenter: HistoryRoundEventPresenter,
    private val settlementTemplates: WinSettlementPresentationTemplateRegistry,
) : Screen(Text.translatable(MinecraftHistoryScreenKeys.ROUND_TITLE)) {
    /** 固定區與內容區共用幾何。 */
    private var layout = HistoryRoundEventsLayout.measure(width = 0, height = 0, contentTop = 0)

    /** 目前事件頁的捲動偏移。 */
    private var scroll = 0.0

    /** 已顯示頁起點；成功換頁時才更新。 */
    private var displayedStart: Int? = null

    /** 是否正拖曳捲軸。 */
    private var dragging = false

    /** 滑塊內的連續抓取偏移。 */
    private var grabOffset = 0.0

    /** 上一頁控制項。 */
    private var previousButton: ButtonWidget? = null

    /** 下一頁控制項。 */
    private var nextButton: ButtonWidget? = null

    /** 重新整理或重試控制項。 */
    private var refreshButton: ButtonWidget? = null

    /** 初始牌面導航。 */
    private var initialButton: ButtonWidget? = null

    /** 卡片內查看局面與載入和牌手牌的按鈕，依交易索引與是否為和牌手牌建立；隨內容捲動，由本畫面在內容裁切範圍內繪製。 */
    private val stateButtons = mutableMapOf<Pair<Int, Boolean>, RestartableMarqueeButtonWidget>()

    /** 完整手牌與狀態頁共用的牌組繪製。 */
    private val tileGroups = HistoryTileGroupRenderer(
        faces = tileFaceRenderer,
        assets = tileAssetRegistry,
        markerDisplays = session.discardMarkers,
        displayNames = session.tileDisplayNames,
        networkRegistries = session.networkRegistries,
    )

    /** 建立固定控制項並恢復此局位置。 */
    override fun init() {
        val frame = HistoryPageFrame(windowWidth = width, windowHeight = height)
        val timingLines = textRenderer.wrapLines(timingText(), frame.contentWidth).size
        layout = HistoryRoundEventsLayout.measure(width = width, height = height, contentTop = TIMING_TOP + timingLines * LINE_HEIGHT + TIMING_BOTTOM_GAP)
        scroll = currentRound()?.eventScrollOffset ?: 0.0
        displayedStart = currentRound()?.events?.transactions?.firstOrNull()?.index
        dragging = false
        stateButtons.clear()
        clearChildren()
        val previous = layout.footerSlot(0)
        previousButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.PREVIOUS)) {
                if (session.controller.canQueryRound()) {
                    if (HistoryPaging.jumpsToEnd()) session.controller.firstRoundPage() else session.controller.previousRoundPage()
                }
            }.dimensions(previous.x, previous.y, previous.width, previous.height).build(),
        )
        val next = layout.footerSlot(2)
        nextButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.NEXT)) {
                if (session.controller.canQueryRound()) {
                    if (HistoryPaging.jumpsToEnd()) session.controller.lastRoundPage() else session.controller.nextRoundPage()
                }
            }.dimensions(next.x, next.y, next.width, next.height).build(),
        )
        val back = layout.footerSlot(HistoryPageFrame.LAST_SLOT)
        addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.BACK)) { session.backToSummary() }
                .dimensions(back.x, back.y, back.width, back.height)
                .build(),
        )
        val headerButtonWidth = ((layout.contentWidth - HEADER_BUTTON_GAP) / 2).coerceAtMost(HEADER_BUTTON_WIDTH).coerceAtLeast(1)
        refreshButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.REFRESH)) {
                if (currentRound()?.eventsStatus is HistoryBrowseStatus.Failed) {
                    if (session.controller.canRetry()) session.controller.retry()
                } else if (session.controller.canQueryRound()) {
                    session.controller.refreshRound()
                }
            }.dimensions(layout.right - headerButtonWidth, HEADER_BUTTON_TOP, headerButtonWidth, FormScreenLayout.BUTTON_HEIGHT).build(),
        )
        initialButton = addDrawableChild(
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.STATE_INITIAL_OPEN)) {
                if (session.controller.canQueryRound()) session.openRoundState(HistoryRoundPositionDto.Initial)
            }.dimensions(layout.left, HEADER_BUTTON_TOP, headerButtonWidth, FormScreenLayout.BUTTON_HEIGHT).build(),
        )
    }

    /**
     * 取得卡片內的查看局面或載入和牌手牌按鈕；同一筆交易重複使用同一顆按鈕。
     *
     * @param index 交易索引。
     * @param inline 是否為留在事件頁展開和牌手牌的按鈕；否則為開啟局面頁的放大鏡按鈕。
     * @return 按鈕；位置由繪製時決定。
     */
    private fun stateButton(index: Int, inline: Boolean): RestartableMarqueeButtonWidget = stateButtons.getOrPut(index to inline) {
        if (inline) {
            val label = Text.translatable(MinecraftHistoryScreenKeys.STATE_LOAD_WINNING_HAND)
            RestartableMarqueeButtonWidget.builder(label) {
                if (session.controller.canQueryRound()) session.controller.loadRoundStateForDetails(index)
            }.dimensions(0, 0, textRenderer.getWidth(label) + MINI_BUTTON_PADDING * 2, MINI_BUTTON_SIZE).build().also {
                it.tooltip = Tooltip.of(ControlTooltip.description(Text.translatable(MinecraftHistoryScreenKeys.STATE_LOAD_WINNING_HAND_TOOLTIP)))
            }
        } else {
            RestartableMarqueeButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.STATE_OPEN)) {
                if (session.controller.canQueryRound()) session.openRoundState(HistoryRoundPositionDto.AfterTransaction(index))
            }.dimensions(0, 0, MINI_BUTTON_SIZE, MINI_BUTTON_SIZE).build().also {
                it.icon = PixelIcon.MAGNIFIER
                it.tooltip = Tooltip.of(
                    Text.empty()
                        .append(Text.translatable(MinecraftHistoryScreenKeys.STATE_OPEN_TOOLTIP).formatted(Formatting.GRAY))
                        .append("\n\n")
                        .append(Text.translatable(MinecraftHistoryScreenKeys.STATE_OPEN_HINT).formatted(Formatting.GRAY)),
                )
            }
        }
    }

    /** 歷史瀏覽不暫停整合伺服器。 */
    override fun shouldPause(): Boolean = false

    /** Escape 與返回按鈕均回到原摘要。 */
    override fun close() {
        session.backToSummary()
    }

    /** 正常子頁導航不取消共用工作。 */
    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /**
     * 繪製固定資訊、卡片、捲軸及可見行 tooltip。
     *
     * @param context 繪製上下文。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param delta 幀內插值。
     */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val round = currentRound()
        val status = round?.eventsStatus ?: HistoryBrowseStatus.Failed(HistoryBrowseFailure.NOT_AVAILABLE)
        val heading = title.copy().append(" • ").append(Text.translatable(MinecraftHistoryScreenKeys.ROUND_NUMBER, round?.roundNumber ?: "—"))
        ScreenRendering.title(context, textRenderer, heading, width)
        textRenderer.wrapLines(timingText(), layout.contentWidth).forEachIndexed { index, line ->
            context.drawTextWithShadow(textRenderer, line, layout.left, TIMING_TOP + index * LINE_HEIGHT, MUTED_COLOR)
        }
        updateButtons(status)
        val events = round?.events?.takeIf { status == HistoryBrowseStatus.Ready }
        val cards = events?.let(::buildCards).orEmpty()
        val hint = events?.let(::resultHint)
        val height = contentHeight(cards, hint)
        if (events != null) {
            val start = round.eventStartIndices.last()
            if (displayedStart != start) {
                scroll = round.eventScrollOffset
                displayedStart = start
                dragging = false
            }
            scroll = layout.clampScroll(scroll, height)
            session.controller.rememberRoundPosition(scroll)
        }
        var tooltip: List<Text>? = null
        stateButtons.values.forEach { it.visible = false }
        context.enableScissor(layout.left, layout.contentTop, layout.right, layout.contentBottom)
        when (status) {
            HistoryBrowseStatus.Idle, HistoryBrowseStatus.Loading -> drawNotice(context, Text.translatable(MinecraftHistoryScreenKeys.LOADING), layout.contentTop + 12, TEXT_COLOR)
            is HistoryBrowseStatus.Failed -> drawNotice(context, Text.translatable(status.reason.translationKey), layout.contentTop + 12, ERROR_COLOR)
            HistoryBrowseStatus.Ready -> {
                tooltip = renderCards(context, cards, mouseX, mouseY, delta)
                val bottom = layout.contentTop + 4 + cards.sumOf { it.height } - scroll.toInt()
                if (cards.isEmpty()) drawNotice(context, Text.translatable(MinecraftHistoryScreenKeys.ROUND_NO_EVENTS), bottom, MUTED_COLOR)
                hint?.let { drawNotice(context, it, bottom + if (cards.isEmpty()) 16 else 4, MUTED_COLOR) }
            }
        }
        context.disableScissor()
        if (events != null) renderScrollbar(context, height)
        renderPage(context, round)
        super.render(context, mouseX, mouseY, delta)
        if (!dragging) tooltip?.let { context.drawTooltip(textRenderer, it, mouseX, mouseY) }
    }

    /**
     * 依共用查詢狀態停用網路控制項。
     *
     * @param status 目前事件頁狀態。
     */
    private fun updateButtons(status: HistoryBrowseStatus) {
        val round = currentRound()
        val canQuery = session.controller.canQueryRound()
        previousButton?.active = canQuery && status == HistoryBrowseStatus.Ready && (round?.eventStartIndices?.size ?: 0) > 1
        nextButton?.active = canQuery && status == HistoryBrowseStatus.Ready && round?.events?.nextTransactionIndex != null
        val failed = status is HistoryBrowseStatus.Failed
        refreshButton?.active = if (failed) session.controller.canRetry() else canQuery
        refreshButton?.message = Text.translatable(if (failed) MinecraftHistoryScreenKeys.RETRY else MinecraftHistoryScreenKeys.REFRESH)
        val queryStatus = when {
            session.controller.isRefreshCoolingDown() -> Text.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP)
            !canQuery -> Text.translatable(MinecraftHistoryScreenKeys.QUERY_LOADING_TOOLTIP)
            else -> null
        }
        val tooltip = queryStatus?.let(Tooltip::of)
        previousButton?.tooltip = Tooltip.of(HistoryPaging.buttonTooltip(MinecraftHistoryScreenKeys.HINT_PREVIOUS_PAGE, MinecraftHistoryScreenKeys.HINT_FIRST_PAGE, queryStatus))
        nextButton?.tooltip = Tooltip.of(HistoryPaging.buttonTooltip(MinecraftHistoryScreenKeys.HINT_NEXT_PAGE, MinecraftHistoryScreenKeys.HINT_LAST_PAGE, queryStatus))
        refreshButton?.tooltip = tooltip
        initialButton?.active = canQuery && status == HistoryBrowseStatus.Ready
        initialButton?.tooltip = tooltip
    }

    /**
     * 測量繪製與高度共用的卡片列。
     *
     * @param events 成功事件頁。
     * @return 保留交易及事實順序的卡片。
     */
    private fun buildCards(events: HistoryRoundEventsDto): List<Card> {
        val ruleId = session.controller.state.value.summary?.detail?.summary?.ruleId
        return eventPresenter.present(events, ruleId).transactions.map { transaction ->
            val rows = mutableListOf<Row>()

            /**
             * 加入換行文字，頭像僅放於第一列。
             *
             * @param text 完整文字及 tooltip。
             * @param color 文字色。
             * @param indent 卡片內縮排。
             * @param participant 可選頭像來源。
             * @param identifiers 僅供 tooltip 查閱的原始識別碼。
             */
            fun addText(text: Text, color: Int = TEXT_COLOR, indent: Int = 8, participant: HistoryParticipantSummaryDto? = null, identifiers: List<String> = emptyList()) {
                val left = indent + if (participant != null) 14 else 0
                textRenderer.wrapLines(text, (layout.contentWidth - left - 8).coerceAtLeast(1)).forEachIndexed { index, line ->
                    rows += Row.TextLine(line, listOf(text) + identifiers.map { Text.literal(it).formatted(Formatting.GRAY) }, left, color, participant.takeIf { index == 0 }, indent)
                }
            }

            /**
             * 加入具名牌列，過長時換列而不截斷。
             *
             * @param tiles 原始順序牌張。
             * @param labelKey 牌列用途 key。
             */
            fun addTiles(tiles: List<TileFaceViewDto>, labelKey: String) {
                if (tiles.isEmpty()) return
                addText(Text.translatable(labelKey), MUTED_COLOR, 16)
                tiles.chunked(layout.tilesPerRow()).forEach { rows += Row.Tiles(it) }
            }
            val heading = Text.translatable(MinecraftHistoryScreenKeys.ROUND_TRANSACTION, transaction.index + 1, HistoryScreenText.timeOfDay(transaction.occurredAtEpochMillis))
            if (transaction.isOpening) heading.append(" · ").append(Text.translatable(MinecraftHistoryScreenKeys.ROUND_OPENING))
            rows += Row.Heading(heading, transaction.index)
            rows += Row.Space(2)
            if (transaction.facts.isEmpty()) addText(Text.translatable(MinecraftHistoryScreenKeys.ROUND_EMPTY_FACTS), MUTED_COLOR, 16)
            transaction.facts.forEachIndexed { factIndex, fact ->
                val savedOutcome = when (val savedFact = fact.source) {
                    is HistoryReplayFactDto.Completion -> savedFact.outcome
                    is HistoryReplayFactDto.RuleEffect -> savedFact.outcome
                    else -> null
                }
                val participant = fact.actorSeat?.let { participant(events.identity, it) }
                val label = fact.actorSeat?.let { actorText(events.identity, it).copy().append(": ").append(fact.text) } ?: fact.text
                addText(label, TEXT_COLOR, 16, participant, fact.identifiers)
                addTiles(fact.directTiles, MinecraftHistoryScreenKeys.ROUND_DIRECT_TILES)
                addTiles(fact.revealedTiles, MinecraftHistoryScreenKeys.ROUND_REVEALED_TILES)
                fact.seatLines.forEach { line ->
                    val label = Text.translatable(MinecraftHistoryScreenKeys.ROUND_SEAT_LINE, actorText(events.identity, line.seat))
                    val seatParticipant = participant(events.identity, line.seat)
                    val portraitIndent = 24 + textRenderer.getWidth(SEAT_LINE_BULLET)
                    val labelIndent = portraitIndent + if (seatParticipant != null) PORTRAIT_STEP else 0
                    val tileIndent = labelIndent + textRenderer.getWidth(label) + 4
                    val perRow = ((layout.contentWidth - tileIndent - 8) / TILE_STEP).coerceAtLeast(1)
                    line.tiles.chunked(perRow).forEachIndexed { index, chunk ->
                        rows += if (index == 0) {
                            Row.LabeledTiles(label, seatParticipant, chunk, bulletIndent = 24, portraitIndent = portraitIndent, labelIndent = labelIndent, tileIndent = tileIndent)
                        } else {
                            Row.Tiles(chunk, tileIndent)
                        }
                    }
                }
                fact.scoreChanges.forEach { (seat, change) ->
                    val line = Text.translatable(MinecraftHistoryScreenKeys.ROUND_SCORE, actorText(events.identity, seat), eventPresenter.scoreChangeText(change))
                    addText(line, TEXT_COLOR, 24, participant(events.identity, seat))
                }
                fact.outcome?.let { outcome ->
                    addText(Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_REASON, outcome.reasonText), ACCENT_COLOR, 24, identifiers = listOf(outcome.reasonId))
                    outcome.classification?.let { addText(Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_CLASSIFICATION, eventPresenter.classificationText(it)), MUTED_COLOR, 24, identifiers = listOf(it)) }
                    outcome.rows.forEach { row ->
                        val score = Text.translatable(MinecraftHistoryScreenKeys.ROUND_SCORE, actorText(events.identity, row.seatIndex), row.score?.toString() ?: "—")
                        row.scoreChange?.let { score.append(" (").append(eventPresenter.scoreChangeText(it)).append(")") }
                        eventPresenter.settlementStatusText(outcome, row)?.let { score.append(" • ").append(it) }
                        if (row.responsible) score.append(" • ").append(Text.translatable(MinecraftHistoryScreenKeys.ROUND_RESPONSIBLE))
                        val scoreColor = if (row.beneficiary && outcome.classification != RoundCompletionClassification.EXHAUSTIVE_DRAW.name) ACCENT_COLOR else TEXT_COLOR
                        addText(score, scoreColor, 24, participant(events.identity, row.seatIndex))
                        savedOutcome?.winnerDetails?.firstOrNull { it.seatIndex == row.seatIndex }?.let { winner ->
                            addText(Text.translatable(MinecraftHistoryScreenKeys.STATE_WINNING_HAND), MUTED_COLOR, 38)
                            val round = currentRound()
                            val position = HistoryRoundPositionDto.AfterTransaction(transaction.index)
                            val state = round?.state?.takeIf { round.stateStatus == HistoryBrowseStatus.Ready && it.position == position }
                            val handWidth = (layout.contentWidth - 54).coerceAtLeast(1).toFloat()
                            val hand = state?.let { HistoryRoundStatePresenter.winningHand(it, winner, handWidth) }
                            when {
                                winner.hand == null -> addText(Text.translatable(MinecraftHistoryScreenKeys.STATE_WINNING_HAND_UNAVAILABLE), MUTED_COLOR, 46)
                                hand != null -> {
                                    rows += Row.Hand(hand, state.tileCatalog)
                                    val winnerState = state.players.firstOrNull { it.initialSeatIndex == winner.seatIndex }
                                    winnerState?.let { HistoryRoundStatePresenter.exposedTiles(state, it, handWidth) }?.let { exposed ->
                                        addText(exposed.heading(session.setAsideDisplays::findAreaName), MUTED_COLOR, 38)
                                        rows += Row.Hand(exposed.layout, state.tileCatalog, exposed.notes { seat -> actorText(events.identity, seat) })
                                    }
                                }
                                state != null -> addText(Text.translatable(MinecraftHistoryScreenKeys.STATE_WINNING_HAND_UNAVAILABLE), MUTED_COLOR, 46)
                                round?.requestedPosition == position && round.stateStatus == HistoryBrowseStatus.Loading -> addText(Text.translatable(MinecraftHistoryScreenKeys.LOADING), MUTED_COLOR, 46)
                                else -> {
                                    val failure = round?.stateStatus as? HistoryBrowseStatus.Failed
                                    if (round?.requestedPosition == position && failure != null) {
                                        addText(Text.translatable(failure.reason.translationKey), ERROR_COLOR, 46)
                                    }
                                    rows += Row.StateLink(transaction.index)
                                }
                            }
                        }
                        if (row.detailFields.isNotEmpty()) {
                            addText(Text.translatable(MinecraftHistoryScreenKeys.ROUND_WIN_DETAILS), MUTED_COLOR, 38)
                            row.detailFields.forEach detailField@{ field ->
                                val tileValue = field.value as? HistoryWinDetailValueDto.TileFaces
                                if (tileValue != null && tileValue.faces.isEmpty()) return@detailField
                                // 只列贏家自己公開牌的欄位，展開的和牌區已在副露旁畫出這些牌。
                                if (tileValue != null && eventPresenter.isWinnerTileDetailField(ruleId, field.id, settlementTemplates)) return@detailField
                                eventPresenter.detailLabel(ruleId, field.id, settlementTemplates)?.let {
                                    addText(it, MUTED_COLOR, 46, identifiers = listOf(field.id))
                                }
                                eventPresenter.detailText(field.id, field.value, settlementTemplates).forEach { addText(it, TEXT_COLOR, 46, identifiers = listOf(field.id)) }
                                val faces = when (val value = field.value) {
                                    is HistoryWinDetailValueDto.TileFaces -> value.faces
                                    else -> emptyList()
                                }
                                faces.takeIf { it.isNotEmpty() }?.chunked(((layout.contentWidth - 54) / TILE_STEP).coerceAtLeast(1))?.forEach { rows += Row.Tiles(it, 46) }
                            }
                        } else if (row.beneficiary && outcome.classification == RoundCompletionClassification.WIN.name && !outcome.hasEarlierWinSettlement) {
                            addText(Text.translatable(MinecraftHistoryScreenKeys.ROUND_WIN_DETAILS_UNAVAILABLE), MUTED_COLOR, 38)
                        }
                    }
                }
                rows += Row.Space(4)
            }
            Card(rows, rows.sumOf { it.height } + 12)
        }
    }

    /**
     * 依 replay 身分對應摘要玩家，不以重排後座位猜頭像。
     *
     * @param identity 初始玩家身分。
     * @param seat 初始座位。
     * @return UUID 相符玩家；缺少映射時為 null。
     */
    private fun participant(identity: HistoryReplayIdentityDto, seat: Int): HistoryParticipantSummaryDto? {
        val id = identity.players.firstOrNull { it.initialSeatIndex == seat }?.playerId ?: return null
        return session.controller.state.value.summary?.detail?.summary?.participants?.firstOrNull { it.playerId.equals(id, ignoreCase = true) }
    }

    /**
     * 取得名稱或具名座位 fallback。
     *
     * @param identity 初始玩家身分。
     * @param seat 初始座位。
     * @return 玩家名稱或從 1 開始的座位名稱。
     */
    private fun actorText(identity: HistoryReplayIdentityDto, seat: Int): Text = participant(identity, seat)?.let {
        session.participants.name(it, session.controller.state.value.summary?.detail?.summary?.participants.orEmpty(), session.controller.state.value.summary?.detail?.summary?.matchId)
    } ?: Text.translatable(MinecraftHistoryScreenKeys.ROUND_SEAT, seat + 1)

    /**
     * 繪製已測量卡片列。
     *
     * @param context 繪製上下文。
     * @param cards 卡片。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param delta 畫面更新比例。
     * @return 可見列 tooltip。
     */
    private fun renderCards(context: DrawContext, cards: List<Card>, mouseX: Int, mouseY: Int, delta: Float): List<Text>? {
        var top = layout.contentTop - scroll.toInt()
        val pointerInside = !dragging && mouseX >= layout.left && mouseX < layout.right && mouseY >= layout.contentTop && mouseY < layout.contentBottom
        val pointerX = if (pointerInside) mouseX else OFFSCREEN_POINTER
        val pointerY = if (pointerInside) mouseY else OFFSCREEN_POINTER
        var tooltip: List<Text>? = null
        cards.forEach { card ->
            if (top + card.height >= layout.contentTop && top < layout.contentBottom) {
                val hovered = !dragging && layout.containsCard(mouseX.toDouble(), mouseY.toDouble(), top, card.height - 4)
                context.fill(layout.left, top, layout.right, top + card.height - 4, if (hovered) CARD_HOVER_COLOR else CARD_COLOR)
                var y = top + 4
                card.rows.forEach { row ->
                    when (row) {
                        is Row.TextLine -> {
                            row.participant?.let { session.participants.render(it, context, layout.left + row.portraitIndent, y - 1, 10, matchId = session.controller.state.value.summary?.detail?.summary?.matchId) }
                            context.drawTextWithShadow(textRenderer, row.text, layout.left + row.indent, y, row.color)
                            if (hovered && mouseY >= y && mouseY < y + row.height) tooltip = row.tooltip
                        }
                        is Row.Tiles -> row.tiles.forEachIndexed { index, tile ->
                            val asset = tile.toHistoryAssetKey(
                                registry = tileAssetRegistry,
                                registries = session.networkRegistries,
                            )
                            val x = layout.left + row.indent + index * TILE_STEP
                            tileFaceRenderer.renderGui(context, asset, x, y, TILE_WIDTH, TILE_HEIGHT, appearance = session.tileAppearance())
                            if (hovered && mouseX >= x && mouseX < x + TILE_WIDTH && mouseY >= y && mouseY < y + TILE_HEIGHT) tooltip = historyTileTooltip(tile.toHistoryNameText(session.tileDisplayNames), catalogIndex = null, takenBy = null, markerLabels = emptyList())
                        }
                        is Row.LabeledTiles -> {
                            val textY = y + (TILE_HEIGHT - LINE_HEIGHT) / 2 + 1
                            context.drawTextWithShadow(textRenderer, SEAT_LINE_BULLET, layout.left + row.bulletIndent, textY, TEXT_COLOR)
                            row.participant?.let { session.participants.render(it, context, layout.left + row.portraitIndent, textY - 1, 10, matchId = session.controller.state.value.summary?.detail?.summary?.matchId) }
                            context.drawTextWithShadow(textRenderer, row.label, layout.left + row.labelIndent, textY, TEXT_COLOR)
                            row.tiles.forEachIndexed { index, tile ->
                                val asset = tile.toHistoryAssetKey(
                                    registry = tileAssetRegistry,
                                    registries = session.networkRegistries,
                                )
                                val x = layout.left + row.tileIndent + index * TILE_STEP
                                tileFaceRenderer.renderGui(context, asset, x, y, TILE_WIDTH, TILE_HEIGHT, appearance = session.tileAppearance())
                                if (hovered && mouseX >= x && mouseX < x + TILE_WIDTH && mouseY >= y && mouseY < y + TILE_HEIGHT) tooltip = historyTileTooltip(tile.toHistoryNameText(session.tileDisplayNames), catalogIndex = null, takenBy = null, markerLabels = emptyList())
                            }
                        }
                        is Row.Space -> Unit
                        is Row.Heading -> {
                            val button = stateButton(row.index, inline = false)
                            button.x = layout.right - CARD_PADDING - button.width
                            button.y = y + (row.height - button.height) / 2
                            val text = HistoryScreenText.trim(row.text.string, (button.x - CARD_PADDING - layout.left - CARD_PADDING).coerceAtLeast(1), textRenderer::getWidth)
                            context.drawTextWithShadow(textRenderer, Text.literal(text), layout.left + CARD_PADDING, y + (row.height - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2, ACCENT_COLOR)
                            renderCardButton(context, button, pointerX, pointerY, delta)
                        }
                        is Row.StateLink -> {
                            val button = stateButton(row.index, inline = true)
                            button.x = layout.left + row.indent
                            button.y = y + (row.height - button.height) / 2
                            renderCardButton(context, button, pointerX, pointerY, delta)
                        }
                        is Row.Hand -> {
                            val hit = tileGroups.render(context, row.layout, row.catalog, layout.left + 46, y, mouseX, mouseY, notes = row.notes, appearance = session.tileAppearance())
                            if (hovered) hit?.let { tooltip = it.tooltip }
                        }
                    }
                    y += row.height
                }
            }
            top += card.height
        }
        return tooltip
    }

    /**
     * 繪製卡片內的按鈕；按鈕有任何部分在內容區內就顯示，點擊只在內容區內有效。
     *
     * @param context 繪製上下文。
     * @param button 要繪製的按鈕。
     * @param pointerX 交給按鈕的游標水平座標；游標不在內容區時在畫面外。
     * @param pointerY 交給按鈕的游標垂直座標；游標不在內容區時在畫面外。
     * @param delta 畫面更新比例。
     */
    private fun renderCardButton(context: DrawContext, button: RestartableMarqueeButtonWidget, pointerX: Int, pointerY: Int, delta: Float) {
        button.active = session.controller.canQueryRound()
        button.visible = button.y + button.height > layout.contentTop && button.y < layout.contentBottom
        button.render(context, pointerX, pointerY, delta)
    }

    /**
     * 尚未讀到可用結算的頁尾提示。
     *
     * @param events 事件頁。
     * @return 本頁沒有結果時的提示；有結果時為 null。
     */
    private fun resultHint(events: HistoryRoundEventsDto): Text? {
        val ruleId = session.controller.state.value.summary?.detail?.summary?.ruleId
        if (eventPresenter.present(events, ruleId).transactions.any { transaction -> transaction.facts.any { it.outcome != null } }) return null
        return Text.translatable(if (events.nextTransactionIndex == null) MinecraftHistoryScreenKeys.ROUND_NO_OUTCOME else MinecraftHistoryScreenKeys.ROUND_NO_OUTCOME_YET)
    }

    /**
     * 計算卡片及頁尾提示的完整高度。
     *
     * @param cards 測量後卡片。
     * @param hint 頁尾提示。
     * @return 不小於可視區的高度。
     */
    private fun contentHeight(cards: List<Card>, hint: Text?): Int = (
        4 + cards.sumOf { it.height } + (if (cards.isEmpty()) 16 else 0) +
            (hint?.let { textRenderer.wrapLines(it, layout.contentWidth.coerceAtLeast(1)).size * LINE_HEIGHT + 4 } ?: 0)
        ).coerceAtLeast(layout.viewportHeight)

    /** 成功頁面高度，不使用載入或失敗時的殘留資料。 */
    private fun readyContentHeight(): Int {
        val events = currentRound()?.takeIf { it.eventsStatus == HistoryBrowseStatus.Ready }?.events ?: return layout.viewportHeight
        return contentHeight(buildCards(events), resultHint(events))
    }

    /** 目前選局狀態。 */
    private fun currentRound(): HistoryBrowseRoundState? = session.controller.state.value.let { state -> state.selectedRoundNumber?.let(state.rounds::get) }

    /** 摘要起訖資訊，未知值不補造時間。 */
    private fun timingText(): Text {
        val round = session.controller.state.value.summary?.detail?.rounds?.firstOrNull { it.roundNumber == currentRound()?.roundNumber }
        return Text.translatable(MinecraftHistoryScreenKeys.ROUND_TIMING, HistoryScreenText.endedAt(round?.startedAtEpochMillis), HistoryScreenText.endedAt(round?.endedAtEpochMillis), HistoryScreenText.intervalDuration(round?.startedAtEpochMillis, round?.endedAtEpochMillis))
    }

    /**
     * 在底部中間一格顯示本頁的交易範圍與本局的交易總數。
     *
     * @param context 繪製上下文。
     * @param round 已確認局狀態。
     */
    private fun renderPage(context: DrawContext, round: HistoryBrowseRoundState?) {
        val events = round?.takeIf { it.eventsStatus == HistoryBrowseStatus.Ready }?.events
        val first = events?.transactions?.firstOrNull()?.index?.plus(1)?.toString() ?: "—"
        val last = events?.transactions?.lastOrNull()?.index?.plus(1)?.toString() ?: "—"
        HistoryPaging.renderRange(
            context = context,
            textRenderer = textRenderer,
            bounds = layout.footerSlot(1),
            primary = Text.translatable(MinecraftHistoryScreenKeys.ROUND_PAGE_RANGE, first, last),
            secondary = HistoryPaging.totalText(round?.transactionCount),
        )
    }

    /**
     * 繪製與拖曳相同幾何的捲軸。
     *
     * @param context 繪製上下文。
     * @param contentHeight 本頁高度。
     */
    private fun renderScrollbar(context: DrawContext, contentHeight: Int) {
        val bar = layout.scrollbar(contentHeight, scroll)
        if (bar.maximumScroll <= 0 || layout.contentBottom <= layout.contentTop) return
        VerticalScrollbar.render(context, layout.scrollbarBounds().x, bar)
    }

    /**
     * 顯示可換行的狀態提示。
     *
     * @param context 繪製上下文。
     * @param text 提示。
     * @param y 上界。
     * @param color 文字色。
     */
    private fun drawNotice(context: DrawContext, text: Text, y: Int, color: Int) {
        textRenderer.wrapLines(text, layout.contentWidth.coerceAtLeast(1)).forEachIndexed { index, line -> context.drawTextWithShadow(textRenderer, line, layout.left, y + index * LINE_HEIGHT, color) }
    }

    /**
     * 處理滾輪，載入期間保留位置。
     *
     * @param mouseX 水平座標。
     * @param mouseY 垂直座標。
     * @param amount 滾輪增量。
     * @return 是否已處理。
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (mouseY < layout.contentTop || mouseY >= layout.contentBottom) return super.mouseScrolled(mouseX, mouseY, amount)
        if (currentRound()?.eventsStatus == HistoryBrowseStatus.Ready) {
            scroll = layout.clampScroll(scroll - amount * 18.0, readyContentHeight())
            session.controller.rememberRoundPosition(scroll)
        }
        return true
    }

    /**
     * 開始捲軸拖曳。
     *
     * @param mouseX 水平座標。
     * @param mouseY 垂直座標。
     * @param button 按鍵。
     * @return 是否已處理。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val height = readyContentHeight()
        val bar = layout.scrollbar(height, scroll)
        if (button == 0 && currentRound()?.eventsStatus == HistoryBrowseStatus.Ready && bar.maximumScroll > 0 && layout.isOverScrollbar(mouseX, mouseY)) {
            dragging = true
            grabOffset = layout.grabOffset(height, scroll, mouseY)
            updateDrag(mouseY)
            return true
        }
        val insideContent = mouseX >= layout.left && mouseX < layout.right && mouseY >= layout.contentTop && mouseY < layout.contentBottom
        if (insideContent && currentRound()?.eventsStatus == HistoryBrowseStatus.Ready) {
            stateButtons.values.firstOrNull { it.visible && it.mouseClicked(mouseX, mouseY, button) }?.let { return true }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /**
     * 更新拖曳位置。
     *
     * @param mouseY 游標垂直座標。
     */
    private fun updateDrag(mouseY: Double) {
        if (currentRound()?.eventsStatus != HistoryBrowseStatus.Ready) return
        scroll = layout.scrollbar(readyContentHeight(), scroll).scrollIndexFor(mouseY, grabOffset).toDouble()
        session.controller.rememberRoundPosition(scroll)
    }

    /**
     * 拖曳時不將輸入送往底層控制項。
     *
     * @param mouseX 水平座標。
     * @param mouseY 垂直座標。
     * @param button 按鍵。
     * @param deltaX 水平位移。
     * @param deltaY 垂直位移。
     * @return 是否已處理。
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
     * @return 是否已處理。
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = dragging && button == 0
        dragging = false
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    /**
     * 一張已測量卡片。
     *
     * @property rows 共用繪製與高度計算的列。
     * @property height 包含內距及卡片間距的高度。
     */
    private data class Card(
        val rows: List<Row>,
        val height: Int,
    )

    /** 卡片列共用幾何。 */
    private sealed interface Row {
        /** 此列完整高度。 */
        val height: Int

        /**
         * 已換行文字列。
         *
         * @property text 繪製文字。
         * @property tooltip 完整文字。
         * @property indent 文字縮排。
         * @property color 文字色。
         * @property participant 可選頭像。
         * @property portraitIndent 頭像縮排。
         */
        data class TextLine(
            val text: OrderedText,
            val tooltip: List<Text>,
            val indent: Int,
            val color: Int,
            val participant: HistoryParticipantSummaryDto?,
            val portraitIndent: Int,
        ) : Row {
            /** 共用文字列高度。 */
            override val height: Int = LINE_HEIGHT
        }

        /**
         * 已分組的一列牌。
         *
         * @property tiles 保留順序與重複的牌張。
         * @property indent 相對卡片左側的牌面縮排。
         */
        data class Tiles(
            val tiles: List<TileFaceViewDto>,
            val indent: Int = 16,
        ) : Row {
            /** 牌面及下方間距。 */
            override val height: Int = TILE_HEIGHT + 4
        }

        /**
         * 開頭帶玩家的一列牌，例如補花紀錄中「• 頭像 AI 3：」後接移出的花牌。
         *
         * @property label 牌前面的玩家名稱。
         * @property participant 玩家頭像來源；沒有對應玩家時為 null。
         * @property tiles 保留順序與重複的牌張。
         * @property bulletIndent 相對卡片左側的項目符號縮排。
         * @property portraitIndent 相對卡片左側的頭像縮排。
         * @property labelIndent 相對卡片左側的名稱縮排。
         * @property tileIndent 相對卡片左側的牌面縮排。
         */
        data class LabeledTiles(
            val label: Text,
            val participant: HistoryParticipantSummaryDto?,
            val tiles: List<TileFaceViewDto>,
            val bulletIndent: Int,
            val portraitIndent: Int,
            val labelIndent: Int,
            val tileIndent: Int,
        ) : Row {
            /** 牌面及下方間距。 */
            override val height: Int = TILE_HEIGHT + 4
        }

        /**
         * 段落間距。
         *
         * @property height 間距高度。
         */
        data class Space(
            override val height: Int,
        ) : Row

        /**
         * 交易標題列：左邊是交易序號與時間，右端是查看局面的放大鏡按鈕。
         *
         * @property text 標題文字。
         * @property index 已確認交易索引。
         */
        data class Heading(
            val text: Text,
            val index: Int,
        ) : Row {
            /** 容得下放大鏡按鈕的列高。 */
            override val height: Int = MINI_BUTTON_SIZE + 2
        }

        /**
         * 載入和牌手牌的按鈕列，留在事件頁展開手牌。
         *
         * @property index 已確認交易索引。
         */
        data class StateLink(
            val index: Int,
        ) : Row {
            /** 容得下按鈕的列高。 */
            override val height: Int = MINI_BUTTON_SIZE + 2

            /** 按鈕縮排。 */
            val indent: Int = 46
        }

        /**
         * 完整和牌手牌群組。
         *
         * @property layout 共用群組排列。
         * @property catalog 經驗證的同筆牌目錄。
         * @property notes 各牌的額外說明，例如副露被鳴的牌來自哪位玩家。
         */
        data class Hand(
            val layout: HistoryTileGroupLayout,
            val catalog: List<TileFaceViewDto>,
            val notes: Map<Int, List<HistoryTileNote>> = emptyMap(),
        ) : Row {
            /** 牌組完整高度與段落間距。 */
            override val height: Int = ceil(layout.height).toInt() + 8
        }
    }

    /** 共用尺寸與配色。 */
    private companion object {
        /** 標題下方「開局狀態」與「重新整理」按鈕的上界。 */
        const val HEADER_BUTTON_TOP = FormScreenLayout.TITLE_BOTTOM

        /** 標題下方兩顆按鈕的最大寬度。 */
        const val HEADER_BUTTON_WIDTH = 100

        /** 標題下方兩顆按鈕之間至少保留的距離。 */
        const val HEADER_BUTTON_GAP = 6

        /** 按鈕下方時間資訊第一行的 Y 座標。 */
        const val TIMING_TOP = HEADER_BUTTON_TOP + FormScreenLayout.BUTTON_HEIGHT + 4

        /** 時間資訊與卡片區之間的距離。 */
        const val TIMING_BOTTOM_GAP = 3

        /** 卡片內小按鈕的高度，也是放大鏡按鈕的邊長。 */
        const val MINI_BUTTON_SIZE = 16

        /** 卡片內文字按鈕左右的留白。 */
        const val MINI_BUTTON_PADDING = 6

        /** 卡片內標題與卡片左右緣的距離。 */
        const val CARD_PADDING = 6

        /** 游標不在內容區時交給卡片內按鈕的座標。 */
        const val OFFSCREEN_POINTER = -1

        /** 文字列高度。 */
        const val LINE_HEIGHT = 11

        /** 牌面寬度。 */
        const val TILE_WIDTH = 18

        /** 牌面高度。 */
        const val TILE_HEIGHT = 24

        /** 牌面起點間距。 */
        const val TILE_STEP = 20

        /** 頭像加上與後方文字的間距。 */
        const val PORTRAIT_STEP = 14

        /** 依玩家列出牌時，每一列開頭的項目符號。 */
        val SEAT_LINE_BULLET: Text = Text.literal("• ")

        /** 一般文字。 */
        const val TEXT_COLOR = 0xffffff

        /** 次要文字。 */
        const val MUTED_COLOR = 0xaaaaaa

        /** 重要資訊。 */
        const val ACCENT_COLOR = 0x8ed5df

        /** 失敗訊息。 */
        const val ERROR_COLOR = 0xff6666

        /** 一般卡片。 */
        const val CARD_COLOR = 0xB0202020.toInt()

        /** 指向卡片。 */
        const val CARD_HOVER_COLOR = 0xB0343434.toInt()
    }
}
