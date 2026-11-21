package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundPositionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
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
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 顯示已授權的單局交易及保存結果，不在客戶端重算規則或計分。
 *
 * @property session 共用導航與查詢生命週期。
 * @property tileFaceRenderer 共用 GUI 牌面 renderer。
 * @property tileAssetRegistry 牌種素材映射來源。
 * @property eventPresenter 已驗證事件的呈現 helper。
 * @param settlementTemplates 明細欄位標題的規則模板來源。
 */
internal class HistoryRoundEventsScreen(
    private val session: HistoryBrowseSession,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val eventPresenter: HistoryRoundEventPresenter,
    settlementTemplates: WinSettlementPresentationTemplateRegistry,
) : Screen(Component.translatable(MinecraftHistoryScreenKeys.ROUND_TITLE)) {
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
    private var previousButton: Button? = null

    /** 下一頁控制項。 */
    private var nextButton: Button? = null

    /** 重新整理或重試控制項。 */
    private var refreshButton: Button? = null

    /** 初始牌面導航。 */
    private var initialButton: Button? = null

    /** 從初始牌面開始回放。 */
    private var replayButton: Button? = null

    /** 卡片標題列從該筆交易後開始回放的按鈕，依交易索引建立；隨內容捲動，由本畫面在內容裁切範圍內繪製。 */
    private val replayButtons = mutableMapOf<Int, RestartableMarqueeButtonWidget>()

    /** 卡片內查看局面與載入和牌手牌的按鈕，依交易索引與是否為和牌手牌建立；隨內容捲動，由本畫面在內容裁切範圍內繪製。 */
    private val stateButtons = mutableMapOf<Pair<Int, Boolean>, RestartableMarqueeButtonWidget>()

    /** 交易卡片中保存結算的各列，與回放的結算卡片共用。 */
    private val outcomeRows = HistoryOutcomeCardRows(eventPresenter, settlementTemplates, session.setAsideDisplays)

    /** 卡片中文字、牌列與和牌手牌的繪製，與回放的結算卡片共用。 */
    private val rowRenderer by lazy { HistoryCardRowRenderer(session, font, tileFaceRenderer, tileAssetRegistry, tileGroups) }

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
        val timingLines = font.split(timingText(), frame.contentWidth).size
        layout = HistoryRoundEventsLayout.measure(width = width, height = height, contentTop = TIMING_TOP + timingLines * HistoryCardStyle.LINE_HEIGHT + TIMING_BOTTOM_GAP)
        scroll = currentRound()?.eventScrollOffset ?: 0.0
        displayedStart = currentRound()?.events?.transactions?.firstOrNull()?.index
        dragging = false
        stateButtons.clear()
        replayButtons.clear()
        clearWidgets()
        val previous = layout.footerSlot(0)
        previousButton = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.PREVIOUS)) {
                if (session.controller.canQueryRound()) {
                    if (HistoryPaging.jumpsToEnd()) session.controller.firstRoundPage() else session.controller.previousRoundPage()
                }
            }.dimensions(previous.x, previous.y, previous.width, previous.height).build(),
        )
        val next = layout.footerSlot(2)
        nextButton = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.NEXT)) {
                if (session.controller.canQueryRound()) {
                    if (HistoryPaging.jumpsToEnd()) session.controller.lastRoundPage() else session.controller.nextRoundPage()
                }
            }.dimensions(next.x, next.y, next.width, next.height).build(),
        )
        val back = layout.footerSlot(HistoryPageFrame.LAST_SLOT)
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.BACK)) { session.backToSummary() }
                .dimensions(back.x, back.y, back.width, back.height)
                .build(),
        )
        val headerButtonWidth = ((layout.contentWidth - HEADER_BUTTON_GAP * 2) / HEADER_BUTTONS).coerceAtMost(HEADER_BUTTON_WIDTH).coerceAtLeast(1)
        refreshButton = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.REFRESH)) {
                if (currentRound()?.eventsStatus is HistoryBrowseStatus.Failed) {
                    if (session.controller.canRetry()) session.controller.retry()
                } else if (session.controller.canQueryRound()) {
                    session.controller.refreshRound()
                }
            }.dimensions(layout.right - headerButtonWidth, HEADER_BUTTON_TOP, headerButtonWidth, FormScreenLayout.BUTTON_HEIGHT).build(),
        )
        initialButton = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.STATE_INITIAL_OPEN)) {
                if (session.controller.canQueryRound()) session.openRoundState(HistoryRoundPositionDto.Initial)
            }.dimensions(layout.left, HEADER_BUTTON_TOP, headerButtonWidth, FormScreenLayout.BUTTON_HEIGHT).build(),
        )
        replayButton = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_OPEN)) { session.openReplay(0) }
                .dimensions(layout.left + headerButtonWidth + HEADER_BUTTON_GAP, HEADER_BUTTON_TOP, headerButtonWidth, FormScreenLayout.BUTTON_HEIGHT)
                .build(),
        )
    }

    /**
     * 取得卡片標題列從該筆交易後開始回放的按鈕；同一筆交易重複使用同一顆按鈕。
     *
     * @param index 交易索引。
     * @return 按鈕；位置由繪製時決定。
     */
    private fun replayButton(index: Int): RestartableMarqueeButtonWidget = replayButtons.getOrPut(index) {
        RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.REPLAY_OPEN)) { session.openReplay(index + 1) }
            .dimensions(0, 0, HistoryCardStyle.MINI_BUTTON_SIZE, HistoryCardStyle.MINI_BUTTON_SIZE)
            .build()
            .also {
                it.icon = PixelIcon.PLAY
                it.tooltip = Tooltip.create(replayTooltip(MinecraftHistoryScreenKeys.REPLAY_OPEN_HERE_TOOLTIP, ruleInstalled()))
                it.visible = false
                it.clickClip = { x, y -> layout.containsCard(x, y, layout.contentTop, layout.viewportHeight) }
                addWidget(it)
            }
    }

    /**
     * 取得卡片內的查看局面或載入和牌手牌按鈕；同一筆交易重複使用同一顆按鈕。
     *
     * @param index 交易索引。
     * @param inline 是否為留在事件頁展開和牌手牌的按鈕；否則為開啟局面頁的放大鏡按鈕。
     * @return 按鈕；位置由繪製時決定。
     */
    private fun stateButton(index: Int, inline: Boolean): RestartableMarqueeButtonWidget = stateButtons.getOrPut(index to inline) {
        val widget = if (inline) {
            val label = Component.translatable(MinecraftHistoryScreenKeys.STATE_LOAD_WINNING_HAND)
            RestartableMarqueeButtonWidget.builder(label) {
                if (session.controller.canQueryRound()) session.controller.loadRoundStateForDetails(index)
            }.dimensions(0, 0, font.width(label) + MINI_BUTTON_PADDING * 2, HistoryCardStyle.MINI_BUTTON_SIZE).build().also {
                it.tooltip = Tooltip.create(ControlTooltip.description(Component.translatable(MinecraftHistoryScreenKeys.STATE_LOAD_WINNING_HAND_TOOLTIP)))
            }
        } else {
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.STATE_OPEN)) {
                if (session.controller.canQueryRound()) session.openRoundState(HistoryRoundPositionDto.AfterTransaction(index))
            }.dimensions(0, 0, HistoryCardStyle.MINI_BUTTON_SIZE, HistoryCardStyle.MINI_BUTTON_SIZE).build().also {
                it.icon = PixelIcon.MAGNIFIER
                it.tooltip = Tooltip.create(
                    Component.empty()
                        .append(Component.translatable(MinecraftHistoryScreenKeys.STATE_OPEN_TOOLTIP).withStyle(ChatFormatting.GRAY))
                        .append("\n\n")
                        .append(Component.translatable(MinecraftHistoryScreenKeys.STATE_OPEN_HINT).withStyle(ChatFormatting.GRAY)),
                )
            }
        }
        widget.visible = false
        widget.clickClip = { x, y -> layout.containsCard(x, y, layout.contentTop, layout.viewportHeight) }
        addWidget(widget)
    }

    /** 歷史瀏覽不暫停整合伺服器。 */
    override fun isPauseScreen(): Boolean = false

    /** Escape 與返回按鈕均回到原摘要。 */
    override fun onClose() {
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
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val round = currentRound()
        val status = round?.eventsStatus ?: HistoryBrowseStatus.Failed(HistoryBrowseFailure.NOT_AVAILABLE)
        val heading = title.copy().append(" • ").append(Component.translatable(MinecraftHistoryScreenKeys.ROUND_NUMBER, round?.roundNumber ?: "—"))
        ScreenRendering.title(context, font, heading, width)
        font.split(timingText(), layout.contentWidth).forEachIndexed { index, line ->
            context.drawString(font, line, layout.left, TIMING_TOP + index * HistoryCardStyle.LINE_HEIGHT, HistoryCardStyle.MUTED_COLOR)
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
        var tooltip: List<Component>? = null
        stateButtons.values.forEach { it.visible = false }
        replayButtons.values.forEach { it.visible = false }
        context.enableScissor(layout.left, layout.contentTop, layout.right, layout.contentBottom)
        when (status) {
            HistoryBrowseStatus.Idle, HistoryBrowseStatus.Loading -> drawNotice(context, Component.translatable(MinecraftHistoryScreenKeys.LOADING), layout.contentTop + 12, HistoryCardStyle.TEXT_COLOR)
            is HistoryBrowseStatus.Failed -> drawNotice(context, Component.translatable(status.reason.translationKey), layout.contentTop + 12, HistoryCardStyle.ERROR_COLOR)
            HistoryBrowseStatus.Ready -> {
                tooltip = renderCards(context, cards, mouseX, mouseY, delta)
                val bottom = layout.contentTop + 4 + cards.sumOf { it.height } - scroll.toInt()
                if (cards.isEmpty()) drawNotice(context, Component.translatable(MinecraftHistoryScreenKeys.ROUND_NO_EVENTS), bottom, HistoryCardStyle.MUTED_COLOR)
                hint?.let { drawNotice(context, it, bottom + if (cards.isEmpty()) 16 else 4, HistoryCardStyle.MUTED_COLOR) }
            }
        }
        context.disableScissor()
        if (events != null) renderScrollbar(context, height)
        renderPage(context, round)
        super.render(context, mouseX, mouseY, delta)
        if (!dragging) tooltip?.let { context.renderComponentTooltip(font, it, mouseX, mouseY) }
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
        refreshButton?.message = Component.translatable(if (failed) MinecraftHistoryScreenKeys.RETRY else MinecraftHistoryScreenKeys.REFRESH)
        val queryStatus = when {
            session.controller.isRefreshCoolingDown() -> Component.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP)
            !canQuery -> Component.translatable(MinecraftHistoryScreenKeys.QUERY_LOADING_TOOLTIP)
            else -> null
        }
        val tooltip = queryStatus?.let(Tooltip::create)
        previousButton?.tooltip = Tooltip.create(HistoryPaging.buttonTooltip(MinecraftHistoryScreenKeys.HINT_PREVIOUS_PAGE, MinecraftHistoryScreenKeys.HINT_FIRST_PAGE, queryStatus))
        nextButton?.tooltip = Tooltip.create(HistoryPaging.buttonTooltip(MinecraftHistoryScreenKeys.HINT_NEXT_PAGE, MinecraftHistoryScreenKeys.HINT_LAST_PAGE, queryStatus))
        refreshButton?.tooltip = tooltip
        initialButton?.active = canQuery && status == HistoryBrowseStatus.Ready
        initialButton?.tooltip = tooltip
        val installed = ruleInstalled()
        replayButton?.active = installed && canQuery && status == HistoryBrowseStatus.Ready
        replayButton?.tooltip = Tooltip.create(replayTooltip(MinecraftHistoryScreenKeys.REPLAY_OPEN_TOOLTIP, installed))
        replayButtons.values.forEach { it.tooltip = Tooltip.create(replayTooltip(MinecraftHistoryScreenKeys.REPLAY_OPEN_HERE_TOOLTIP, installed)) }
    }

    /**
     * 這台電腦是否裝有這場對局使用的規則；沒裝時回放按鈕停用。
     *
     * @return 裝有該規則或無從判斷時為 true。
     */
    private fun ruleInstalled(): Boolean = session.ruleInstalled(session.controller.state.value.summary?.detail?.summary?.ruleId)

    /**
     * 回放按鈕的提示框；這台電腦沒有這場對局的規則時寫出無法回放的原因。
     *
     * @param descriptionKey 按鈕說明的翻譯鍵。
     * @param installed 這台電腦是否裝有這場對局的規則。
     * @return 提示文字。
     */
    private fun replayTooltip(descriptionKey: String, installed: Boolean): Component = ControlTooltip.description(
        description = Component.translatable(descriptionKey),
        notice = Component.translatable(MinecraftHistoryScreenKeys.REPLAY_RULE_NOT_INSTALLED).takeUnless { installed },
    )

    /**
     * 測量繪製與高度共用的卡片列。
     *
     * @param events 成功事件頁。
     * @return 保留交易及事實順序的卡片。
     */
    private fun buildCards(events: HistoryRoundEventsDto): List<Card> {
        val ruleId = session.controller.state.value.summary?.detail?.summary?.ruleId
        return eventPresenter.present(events, ruleId).transactions.map { transaction ->
            val builder = HistoryCardRowsBuilder(font, layout.contentWidth)
            val rows = builder.rows

            /**
             * 加入換行文字，頭像僅放於第一列。
             *
             * @param text 完整文字及 tooltip。
             * @param color 文字色。
             * @param indent 卡片內縮排。
             * @param participant 可選頭像來源。
             * @param identifiers 僅供 tooltip 查閱的原始識別碼。
             */
            fun addText(text: Component, color: Int = HistoryCardStyle.TEXT_COLOR, indent: Int = 8, participant: HistoryParticipantSummaryDto? = null, identifiers: List<String> = emptyList()) {
                builder.addText(text, color, indent, participant, identifiers)
            }

            /**
             * 加入具名牌列，過長時換列而不截斷。
             *
             * @param tiles 原始順序牌張。
             * @param labelKey 牌列用途 key。
             */
            fun addTiles(tiles: List<TileFaceViewDto>, labelKey: String) {
                if (tiles.isEmpty()) return
                addText(Component.translatable(labelKey), HistoryCardStyle.MUTED_COLOR, 16)
                tiles.chunked(layout.tilesPerRow()).forEach { rows += HistoryCardRow.Tiles(it) }
            }
            val heading = Component.translatable(MinecraftHistoryScreenKeys.ROUND_TRANSACTION, transaction.index + 1, HistoryScreenText.timeOfDay(transaction.occurredAtEpochMillis))
            if (transaction.isOpening) heading.append(" · ").append(Component.translatable(MinecraftHistoryScreenKeys.ROUND_OPENING))
            rows += HistoryCardRow.Heading(heading, transaction.index)
            rows += HistoryCardRow.Space(2)
            if (transaction.facts.isEmpty()) addText(Component.translatable(MinecraftHistoryScreenKeys.ROUND_EMPTY_FACTS), HistoryCardStyle.MUTED_COLOR, 16)
            transaction.facts.forEachIndexed { factIndex, fact ->
                val savedOutcome = when (val savedFact = fact.source) {
                    is HistoryReplayFactDto.Completion -> savedFact.outcome
                    is HistoryReplayFactDto.RuleEffect -> savedFact.outcome
                    else -> null
                }
                val participant = fact.actorSeat?.let { participant(events.identity, it) }
                val label = fact.actorSeat?.let { actorText(events.identity, it).copy().append(": ").append(fact.text) } ?: fact.text
                addText(label, HistoryCardStyle.TEXT_COLOR, 16, participant, fact.identifiers)
                addTiles(fact.directTiles, MinecraftHistoryScreenKeys.ROUND_DIRECT_TILES)
                addTiles(fact.revealedTiles, MinecraftHistoryScreenKeys.ROUND_REVEALED_TILES)
                fact.seatLines.forEach { line ->
                    val label = Component.translatable(MinecraftHistoryScreenKeys.ROUND_SEAT_LINE, actorText(events.identity, line.seat))
                    val seatParticipant = participant(events.identity, line.seat)
                    val portraitIndent = 24 + font.width(HistoryCardStyle.SEAT_LINE_BULLET)
                    val labelIndent = portraitIndent + if (seatParticipant != null) HistoryCardStyle.PORTRAIT_STEP else 0
                    val tileIndent = labelIndent + font.width(label) + 4
                    val perRow = ((layout.contentWidth - tileIndent - 8) / HistoryCardStyle.TILE_STEP).coerceAtLeast(1)
                    line.tiles.chunked(perRow).forEachIndexed { index, chunk ->
                        rows += if (index == 0) {
                            HistoryCardRow.LabeledTiles(label, seatParticipant, chunk, bulletIndent = 24, portraitIndent = portraitIndent, labelIndent = labelIndent, tileIndent = tileIndent)
                        } else {
                            HistoryCardRow.Tiles(chunk, tileIndent)
                        }
                    }
                }
                fact.scoreChanges.forEach { (seat, change) ->
                    val line = Component.translatable(MinecraftHistoryScreenKeys.ROUND_SCORE, actorText(events.identity, seat), eventPresenter.scoreChangeText(change))
                    addText(line, HistoryCardStyle.TEXT_COLOR, 24, participant(events.identity, seat))
                }
                fact.outcome?.let { outcome ->
                    outcomeRows.add(
                        builder = builder,
                        outcome = outcome,
                        savedOutcome = savedOutcome,
                        ruleId = ruleId,
                        transactionIndex = transaction.index,
                        actorText = { seat -> actorText(events.identity, seat) },
                        participant = { seat -> participant(events.identity, seat) },
                        winningHand = { winningHandSource(transaction.index) },
                    )
                }
                rows += HistoryCardRow.Space(4)
            }
            Card(rows, rows.sumOf { it.height } + 12)
        }
    }

    /**
     * 交易卡片畫出和牌手牌所需的牌面：已讀到該筆交易後的牌面時直接使用，正在讀取或讀取失敗時如實顯示，否則畫出讀取按鈕。
     *
     * @param transactionIndex 交易索引。
     * @return 牌面來源。
     */
    private fun winningHandSource(transactionIndex: Int): HistoryWinningHandSource {
        val round = currentRound()
        val position = HistoryRoundPositionDto.AfterTransaction(transactionIndex)
        round?.state?.takeIf { round.stateStatus == HistoryBrowseStatus.Ready && it.position == position }?.let { return HistoryWinningHandSource.Ready(it) }
        if (round?.requestedPosition != position) return HistoryWinningHandSource.NotLoaded
        return when (val status = round.stateStatus) {
            HistoryBrowseStatus.Loading -> HistoryWinningHandSource.Loading
            is HistoryBrowseStatus.Failed -> HistoryWinningHandSource.Failed(status.reason)
            else -> HistoryWinningHandSource.NotLoaded
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
    private fun actorText(identity: HistoryReplayIdentityDto, seat: Int): Component = participant(identity, seat)?.let {
        session.participants.name(it, session.controller.state.value.summary?.detail?.summary?.participants.orEmpty(), session.controller.state.value.summary?.detail?.summary?.matchId)
    } ?: Component.translatable(MinecraftHistoryScreenKeys.ROUND_SEAT, seat + 1)

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
    private fun renderCards(context: GuiGraphics, cards: List<Card>, mouseX: Int, mouseY: Int, delta: Float): List<Component>? {
        var top = layout.contentTop - scroll.toInt()
        val pointerInside = !dragging && mouseX >= layout.left && mouseX < layout.right && mouseY >= layout.contentTop && mouseY < layout.contentBottom
        val pointerX = if (pointerInside) mouseX else OFFSCREEN_POINTER
        val pointerY = if (pointerInside) mouseY else OFFSCREEN_POINTER
        var tooltip: List<Component>? = null
        val canQuery = session.controller.canQueryRound()
        val canReplay = canQuery && ruleInstalled()
        cards.forEach { card ->
            if (top + card.height >= layout.contentTop && top < layout.contentBottom) {
                val hovered = !dragging && layout.containsCard(mouseX.toDouble(), mouseY.toDouble(), top, card.height - 4)
                context.fill(layout.left, top, layout.right, top + card.height - 4, if (hovered) CARD_HOVER_COLOR else CARD_COLOR)
                var y = top + 4
                card.rows.forEach { row ->
                    when (row) {
                        is HistoryCardRow.TextLine, is HistoryCardRow.Tiles, is HistoryCardRow.LabeledTiles, is HistoryCardRow.Space, is HistoryCardRow.Hand -> {
                            rowRenderer.render(context, row, layout.left, y, hovered, mouseX, mouseY)?.let { tooltip = it }
                        }
                        is HistoryCardRow.Heading -> {
                            val button = stateButton(row.index, inline = false)
                            button.x = layout.right - CARD_PADDING - button.width
                            button.y = y + (row.height - button.height) / 2
                            val replay = replayButton(row.index)
                            replay.x = button.x - CARD_BUTTON_GAP - replay.width
                            replay.y = button.y
                            val text = HistoryScreenText.trim(row.text.string, (replay.x - CARD_PADDING - layout.left - CARD_PADDING).coerceAtLeast(1), font::width)
                            context.drawString(font, Component.literal(text), layout.left + CARD_PADDING, y + (row.height - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2, HistoryCardStyle.ACCENT_COLOR)
                            renderCardButton(context, replay, canReplay, pointerX, pointerY, delta)
                            renderCardButton(context, button, canQuery, pointerX, pointerY, delta)
                        }
                        is HistoryCardRow.StateLink -> {
                            val button = stateButton(row.index, inline = true)
                            button.x = layout.left + row.indent
                            button.y = y + (row.height - button.height) / 2
                            renderCardButton(context, button, canQuery, pointerX, pointerY, delta)
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
     * @param active 按鈕是否可點。
     * @param pointerX 交給按鈕的游標水平座標；游標不在內容區時在畫面外。
     * @param pointerY 交給按鈕的游標垂直座標；游標不在內容區時在畫面外。
     * @param delta 畫面更新比例。
     */
    private fun renderCardButton(context: GuiGraphics, button: RestartableMarqueeButtonWidget, active: Boolean, pointerX: Int, pointerY: Int, delta: Float) {
        button.active = active
        button.visible = button.y + button.height > layout.contentTop && button.y < layout.contentBottom
        button.render(context, pointerX, pointerY, delta)
    }

    /**
     * 尚未讀到可用結算的頁尾提示。
     *
     * @param events 事件頁。
     * @return 本頁沒有結果時的提示；有結果時為 null。
     */
    private fun resultHint(events: HistoryRoundEventsDto): Component? {
        val ruleId = session.controller.state.value.summary?.detail?.summary?.ruleId
        if (eventPresenter.present(events, ruleId).transactions.any { transaction -> transaction.facts.any { it.outcome != null } }) return null
        return Component.translatable(if (events.nextTransactionIndex == null) MinecraftHistoryScreenKeys.ROUND_NO_OUTCOME else MinecraftHistoryScreenKeys.ROUND_NO_OUTCOME_YET)
    }

    /**
     * 計算卡片及頁尾提示的完整高度。
     *
     * @param cards 測量後卡片。
     * @param hint 頁尾提示。
     * @return 不小於可視區的高度。
     */
    private fun contentHeight(cards: List<Card>, hint: Component?): Int = (
        4 + cards.sumOf { it.height } + (if (cards.isEmpty()) 16 else 0) +
            (hint?.let { font.split(it, layout.contentWidth.coerceAtLeast(1)).size * HistoryCardStyle.LINE_HEIGHT + 4 } ?: 0)
        ).coerceAtLeast(layout.viewportHeight)

    /** 成功頁面高度，不使用載入或失敗時的殘留資料。 */
    private fun readyContentHeight(): Int {
        val events = currentRound()?.takeIf { it.eventsStatus == HistoryBrowseStatus.Ready }?.events ?: return layout.viewportHeight
        return contentHeight(buildCards(events), resultHint(events))
    }

    /** 目前選局狀態。 */
    private fun currentRound(): HistoryBrowseRoundState? = session.controller.state.value.let { state -> state.selectedRoundNumber?.let(state.rounds::get) }

    /** 摘要起訖資訊，未知值不補造時間。 */
    private fun timingText(): Component {
        val round = session.controller.state.value.summary?.detail?.rounds?.firstOrNull { it.roundNumber == currentRound()?.roundNumber }
        return Component.translatable(MinecraftHistoryScreenKeys.ROUND_TIMING, HistoryScreenText.endedAt(round?.startedAtEpochMillis), HistoryScreenText.endedAt(round?.endedAtEpochMillis), HistoryScreenText.intervalDuration(round?.startedAtEpochMillis, round?.endedAtEpochMillis))
    }

    /**
     * 在底部中間一格顯示本頁的交易範圍與本局的交易總數。
     *
     * @param context 繪製上下文。
     * @param round 已確認局狀態。
     */
    private fun renderPage(context: GuiGraphics, round: HistoryBrowseRoundState?) {
        val events = round?.takeIf { it.eventsStatus == HistoryBrowseStatus.Ready }?.events
        val first = events?.transactions?.firstOrNull()?.index?.plus(1)?.toString() ?: "—"
        val last = events?.transactions?.lastOrNull()?.index?.plus(1)?.toString() ?: "—"
        HistoryPaging.renderRange(
            context = context,
            textRenderer = font,
            bounds = layout.footerSlot(1),
            primary = Component.translatable(MinecraftHistoryScreenKeys.ROUND_PAGE_RANGE, first, last),
            secondary = HistoryPaging.totalText(round?.transactionCount),
        )
    }

    /**
     * 繪製與拖曳相同幾何的捲軸。
     *
     * @param context 繪製上下文。
     * @param contentHeight 本頁高度。
     */
    private fun renderScrollbar(context: GuiGraphics, contentHeight: Int) {
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
    private fun drawNotice(context: GuiGraphics, text: Component, y: Int, color: Int) {
        font.split(text, layout.contentWidth.coerceAtLeast(1)).forEachIndexed { index, line -> context.drawString(font, line, layout.left, y + index * HistoryCardStyle.LINE_HEIGHT, color) }
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
            (stateButtons.values + replayButtons.values).firstOrNull { it.visible && it.mouseClicked(mouseX, mouseY, button) }?.let { return true }
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
        val rows: List<HistoryCardRow>,
        val height: Int,
    )

    /** 共用尺寸與配色。 */
    private companion object {
        /** 標題下方「開局狀態」與「重新整理」按鈕的上界。 */
        const val HEADER_BUTTON_TOP = FormScreenLayout.TITLE_BOTTOM

        /** 標題下方兩顆按鈕的最大寬度。 */
        const val HEADER_BUTTON_WIDTH = 100

        /** 標題下方兩顆按鈕之間至少保留的距離。 */
        const val HEADER_BUTTON_GAP = 6

        /** 標題列的按鈕數：初始牌面、回放、重新整理。 */
        const val HEADER_BUTTONS = 3

        /** 卡片標題列兩顆小按鈕之間的距離。 */
        const val CARD_BUTTON_GAP = 2

        /** 按鈕下方時間資訊第一行的 Y 座標。 */
        const val TIMING_TOP = HEADER_BUTTON_TOP + FormScreenLayout.BUTTON_HEIGHT + 4

        /** 時間資訊與卡片區之間的距離。 */
        const val TIMING_BOTTOM_GAP = 3

        /** 卡片內文字按鈕左右的留白。 */
        const val MINI_BUTTON_PADDING = 6

        /** 卡片內標題與卡片左右緣的距離。 */
        const val CARD_PADDING = 6

        /** 游標不在內容區時交給卡片內按鈕的座標。 */
        const val OFFSCREEN_POINTER = -1

        /** 一般卡片。 */
        const val CARD_COLOR = 0xB0202020.toInt()

        /** 指向卡片。 */
        const val CARD_HOVER_COLOR = 0xB0343434.toInt()
    }
}
