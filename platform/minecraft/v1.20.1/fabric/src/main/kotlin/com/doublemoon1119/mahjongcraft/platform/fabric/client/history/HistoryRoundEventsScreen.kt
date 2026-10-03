package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayIdentityDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundEventsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinDetailValueDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

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
    private var layout = HistoryRoundEventsLayout.measure(0, 0)

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

    /** 建立固定控制項並恢復此局位置。 */
    override fun init() {
        val timingLines = textRenderer.wrapLines(timingText(), (width - 112).coerceAtLeast(1)).size
        layout = HistoryRoundEventsLayout.measure(width, height, 28 + timingLines * LINE_HEIGHT)
        scroll = currentRound()?.eventScrollOffset ?: 0.0
        displayedStart = currentRound()?.events?.transactions?.firstOrNull()?.index
        dragging = false
        clearChildren()
        val bounds = layout.footerBounds(width)
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.BACK)) { session.backToSummary() }
                .dimensions(bounds[0].x, bounds[0].y, bounds[0].width, bounds[0].height).build(),
        )
        previousButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.PREVIOUS)) {
            if (session.controller.canQueryRound()) session.controller.previousRoundPage()
        }.dimensions(bounds[1].x, bounds[1].y, bounds[1].width, bounds[1].height).build().also(::addDrawableChild)
        nextButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.NEXT)) {
            if (session.controller.canQueryRound()) session.controller.nextRoundPage()
        }.dimensions(bounds[3].x, bounds[3].y, bounds[3].width, bounds[3].height).build().also(::addDrawableChild)
        refreshButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.REFRESH)) {
            if (currentRound()?.eventsStatus is HistoryBrowseStatus.Failed) {
                if (session.controller.canRetry()) session.controller.retry()
            } else if (session.controller.canQueryRound()) {
                session.controller.refreshRound()
            }
        }.dimensions((width - 88).coerceAtLeast(8), 16, 80.coerceAtMost((width - 16).coerceAtLeast(1)), 20).build().also(::addDrawableChild)
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
        context.drawCenteredTextWithShadow(textRenderer, heading, width / 2, 2, TEXT_COLOR)
        textRenderer.wrapLines(timingText(), (width - 112).coerceAtLeast(1)).forEachIndexed { index, line ->
            context.drawTextWithShadow(textRenderer, line, 10, 18 + index * LINE_HEIGHT, MUTED_COLOR)
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
        context.enableScissor(8, layout.contentTop, (width - 8).coerceAtLeast(8), layout.contentBottom)
        when (status) {
            HistoryBrowseStatus.Idle, HistoryBrowseStatus.Loading -> drawNotice(context, Text.translatable(MinecraftHistoryScreenKeys.LOADING), layout.contentTop + 12, TEXT_COLOR)
            is HistoryBrowseStatus.Failed -> drawNotice(context, Text.translatable("${MinecraftHistoryScreenKeys.FAILURE_PREFIX}${status.reason.name.lowercase()}"), layout.contentTop + 12, ERROR_COLOR)
            HistoryBrowseStatus.Ready -> {
                tooltip = renderCards(context, cards, mouseX, mouseY)
                val bottom = layout.contentTop + 4 + cards.sumOf { it.height } - scroll.toInt()
                if (cards.isEmpty()) drawNotice(context, Text.translatable(MinecraftHistoryScreenKeys.ROUND_NO_EVENTS), bottom, MUTED_COLOR)
                hint?.let { drawNotice(context, it, bottom + if (cards.isEmpty()) 16 else 4, MUTED_COLOR) }
            }
        }
        context.disableScissor()
        if (events != null) renderScrollbar(context, height)
        val pageTooltip = renderPage(context, round, mouseX, mouseY)
        super.render(context, mouseX, mouseY, delta)
        if (!dragging) (pageTooltip ?: tooltip)?.let { context.drawTooltip(textRenderer, it, mouseX, mouseY) }
    }

    /**
     * 依共用查詢狀態停用網路控制項。
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
        val tooltip = when {
            session.controller.isRefreshCoolingDown() -> Tooltip.of(Text.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP))
            !canQuery -> Tooltip.of(Text.translatable(MinecraftHistoryScreenKeys.QUERY_LOADING_TOOLTIP))
            else -> null
        }
        previousButton?.tooltip = tooltip
        nextButton?.tooltip = tooltip
        refreshButton?.tooltip = tooltip
    }

    /**
     * 測量繪製與高度共用的卡片列。
     * @param events 成功事件頁。
     * @return 保留交易及事實順序的卡片。
     */
    private fun buildCards(events: HistoryRoundEventsDto): List<Card> {
        val ruleId = session.controller.state.value.summary?.detail?.summary?.ruleId
        return eventPresenter.present(events, ruleId).transactions.map { transaction ->
            val rows = mutableListOf<Row>()

            /**
             * 加入換行文字，頭像僅放於第一列。
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
             * @param tiles 原始順序牌張。
             * @param labelKey 牌列用途 key。
             */
            fun addTiles(tiles: List<TileDto>, labelKey: String) {
                if (tiles.isEmpty()) return
                addText(Text.translatable(labelKey), MUTED_COLOR, 16)
                tiles.chunked(layout.tilesPerRow()).forEach { rows += Row.Tiles(it) }
            }
            val heading = Text.translatable(MinecraftHistoryScreenKeys.ROUND_TRANSACTION, transaction.index + 1, HistoryScreenText.endedAt(transaction.occurredAtEpochMillis))
            if (transaction.isOpening) heading.append(" • ").append(Text.translatable(MinecraftHistoryScreenKeys.ROUND_OPENING))
            addText(heading, ACCENT_COLOR)
            rows += Row.Space(4)
            if (transaction.facts.isEmpty()) addText(Text.translatable(MinecraftHistoryScreenKeys.ROUND_EMPTY_FACTS), MUTED_COLOR, 16)
            transaction.facts.forEach { fact ->
                val participant = fact.actorSeat?.let { participant(events.identity, it) }
                val label = fact.actorSeat?.let { actorText(events.identity, it).copy().append(": ").append(fact.text) } ?: fact.text
                addText(label, TEXT_COLOR, 16, participant, fact.identifiers)
                addTiles(fact.directTiles, MinecraftHistoryScreenKeys.ROUND_DIRECT_TILES)
                addTiles(fact.revealedTiles, MinecraftHistoryScreenKeys.ROUND_REVEALED_TILES)
                fact.outcome?.let { outcome ->
                    addText(Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_REASON, outcome.reasonText), ACCENT_COLOR, 24, identifiers = listOf(outcome.reasonId))
                    outcome.classification?.let { addText(Text.translatable(MinecraftHistoryScreenKeys.ROUND_OUTCOME_CLASSIFICATION, eventPresenter.classificationText(it)), MUTED_COLOR, 24, identifiers = listOf(it)) }
                    outcome.rows.forEach { row ->
                        val score = Text.translatable(MinecraftHistoryScreenKeys.ROUND_SCORE, actorText(events.identity, row.seatIndex), row.score?.toString() ?: "—")
                        row.scoreChange?.let { score.append(" (").append(eventPresenter.scoreChangeText(it)).append(")") }
                        if (row.beneficiary) score.append(" • ").append(Text.translatable(MinecraftHistoryScreenKeys.ROUND_BENEFICIARY))
                        if (row.responsible) score.append(" • ").append(Text.translatable(MinecraftHistoryScreenKeys.ROUND_RESPONSIBLE))
                        addText(score, if (row.beneficiary) ACCENT_COLOR else TEXT_COLOR, 24, participant(events.identity, row.seatIndex))
                        if (row.detailFields.isNotEmpty()) {
                            addText(Text.translatable(MinecraftHistoryScreenKeys.ROUND_WIN_DETAILS), MUTED_COLOR, 38)
                            row.detailFields.forEach detailField@{ field ->
                                val tileValue = field.value as? HistoryWinDetailValueDto.Tiles
                                if (tileValue != null && tileValue.tiles.isEmpty()) return@detailField
                                eventPresenter.detailLabel(row.templateKey, field.id, settlementTemplates)?.let {
                                    addText(it, MUTED_COLOR, 46, identifiers = listOf(field.id))
                                }
                                eventPresenter.detailText(field.value).forEach { addText(it, TEXT_COLOR, 46, identifiers = listOf(field.id)) }
                                (field.value as? HistoryWinDetailValueDto.Tiles)?.tiles?.mapNotNull(events.tileCatalog::getOrNull)
                                    ?.takeIf { it.isNotEmpty() }?.chunked(((layout.contentWidth - 54) / TILE_STEP).coerceAtLeast(1))?.forEach { rows += Row.Tiles(it, 46) }
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
     * @param identity 初始玩家身分。
     * @param seat 初始座位。
     * @return 玩家名稱或從 1 開始的座位名稱。
     */
    private fun actorText(identity: HistoryReplayIdentityDto, seat: Int): Text = participant(identity, seat)?.let {
        session.participants.name(it, session.controller.state.value.summary?.detail?.summary?.participants.orEmpty())
    } ?: Text.translatable(MinecraftHistoryScreenKeys.ROUND_SEAT, seat + 1)

    /**
     * 繪製已測量卡片列。
     * @param context 繪製上下文。
     * @param cards 卡片。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 可見列 tooltip。
     */
    private fun renderCards(context: DrawContext, cards: List<Card>, mouseX: Int, mouseY: Int): List<Text>? {
        var top = layout.contentTop + 4 - scroll.toInt()
        var tooltip: List<Text>? = null
        cards.forEach { card ->
            if (top + card.height >= layout.contentTop && top < layout.contentBottom) {
                val hovered = !dragging && layout.containsCard(mouseX.toDouble(), mouseY.toDouble(), top, card.height - 4)
                context.fill(layout.left, top, layout.cardRight, top + card.height - 4, if (hovered) CARD_HOVER_COLOR else CARD_COLOR)
                var y = top + 4
                card.rows.forEach { row ->
                    when (row) {
                        is Row.TextLine -> {
                            row.participant?.let { session.participants.render(it, context, layout.left + row.portraitIndent, y - 1, 10) }
                            context.drawTextWithShadow(textRenderer, row.text, layout.left + row.indent, y, row.color)
                            if (hovered && mouseY >= y && mouseY < y + row.height) tooltip = row.tooltip
                        }
                        is Row.Tiles -> row.tiles.forEachIndexed { index, tile ->
                            val asset = tile.toDomain().toAssetKey(tileAssetRegistry)
                            val x = layout.left + row.indent + index * TILE_STEP
                            tileFaceRenderer.renderGui(context, asset, x, y, TILE_WIDTH, TILE_HEIGHT)
                            if (hovered && mouseX >= x && mouseX < x + TILE_WIDTH && mouseY >= y && mouseY < y + TILE_HEIGHT) tooltip = listOf(Text.literal(asset))
                        }
                        is Row.Space -> Unit
                    }
                    y += row.height
                }
            }
            top += card.height
        }
        return tooltip
    }

    /**
     * 尚未讀到可用結算的頁尾提示。
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
     * @param cards 測量後卡片。
     * @param hint 頁尾提示。
     * @return 不小於可視區的高度。
     */
    private fun contentHeight(cards: List<Card>, hint: Text?): Int = (
        8 + cards.sumOf { it.height } + (if (cards.isEmpty()) 16 else 0) +
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
     * 顯示已確認頁碼與本頁交易範圍，不猜總數。
     * @param context 繪製上下文。
     * @param round 已確認局狀態。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 指向頁碼時的完整範圍提示。
     */
    private fun renderPage(context: DrawContext, round: HistoryBrowseRoundState?, mouseX: Int, mouseY: Int): List<Text>? {
        val bounds = layout.footerBounds(width)[2]
        val events = round?.takeIf { it.eventsStatus == HistoryBrowseStatus.Ready }?.events
        val first = events?.transactions?.firstOrNull()?.index?.plus(1)?.toString() ?: "—"
        val last = events?.transactions?.lastOrNull()?.index?.plus(1)?.toString() ?: "—"
        val text = Text.translatable(MinecraftHistoryScreenKeys.ROUND_PAGE_RANGE, round?.eventPageNumber ?: 1, first, last)
        val lines = listOf(Text.translatable(MinecraftHistoryScreenKeys.PAGE, round?.eventPageNumber ?: 1), Text.literal("$first–$last"))
        context.enableScissor(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)
        lines.forEachIndexed { index, line ->
            val scale = (bounds.width.toFloat() / textRenderer.getWidth(line).coerceAtLeast(1)).coerceAtMost(1f)
            context.matrices.push()
            context.matrices.translate((bounds.x + bounds.width / 2).toDouble(), (bounds.y + index * LINE_HEIGHT).toDouble(), 0.0)
            context.matrices.scale(scale, scale, 1f)
            context.drawCenteredTextWithShadow(textRenderer, line, 0, 0, TEXT_COLOR)
            context.matrices.pop()
        }
        context.disableScissor()
        return listOf(text).takeIf { mouseX >= bounds.x && mouseX < bounds.x + bounds.width && mouseY >= bounds.y && mouseY < bounds.y + bounds.height }
    }

    /**
     * 繪製與拖曳相同幾何的捲軸。
     * @param context 繪製上下文。
     * @param contentHeight 本頁高度。
     */
    private fun renderScrollbar(context: DrawContext, contentHeight: Int) {
        val bar = layout.scrollbar(contentHeight, scroll)
        if (bar.maximumScroll <= 0 || layout.contentBottom <= layout.contentTop) return
        val bounds = layout.scrollbarBounds()
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0x80505050.toInt())
        context.fill(bounds.x, bar.thumbTop, bounds.x + bounds.width, bar.thumbTop + bar.thumbHeight, 0xffd0d0d0.toInt())
    }

    /**
     * 顯示可換行的狀態提示。
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
     * @param mouseX 水平座標。
     * @param mouseY 垂直座標。
     * @param button 按鍵。
     * @return 是否已處理。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val height = readyContentHeight()
        val bar = layout.scrollbar(height, scroll)
        val bounds = layout.scrollbarBounds()
        if (button == 0 && currentRound()?.eventsStatus == HistoryBrowseStatus.Ready && bar.maximumScroll > 0 && mouseX >= bounds.x && mouseX < bounds.x + bounds.width && mouseY >= bounds.y && mouseY < bounds.y + bounds.height) {
            dragging = true
            grabOffset = layout.grabOffset(height, scroll, mouseY)
            updateDrag(mouseY)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /**
     * 更新拖曳位置。
     * @param mouseY 游標垂直座標。
     */
    private fun updateDrag(mouseY: Double) {
        if (currentRound()?.eventsStatus != HistoryBrowseStatus.Ready) return
        scroll = layout.scrollbar(readyContentHeight(), scroll).scrollIndexFor(mouseY, grabOffset).toDouble()
        session.controller.rememberRoundPosition(scroll)
    }

    /**
     * 拖曳時不將輸入送往底層控制項。
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
         * @property tiles 保留順序與重複的牌張。
         * @property indent 相對卡片左側的牌面縮排。
         */
        data class Tiles(
            val tiles: List<TileDto>,
            val indent: Int = 16,
        ) : Row {
            /** 牌面及下方間距。 */
            override val height: Int = TILE_HEIGHT + 4
        }

        /**
         * 段落間距。
         * @property height 間距高度。
         */
        data class Space(
            override val height: Int,
        ) : Row
    }

    /** 共用尺寸與配色。 */
    private companion object {
        /** 文字列高度。 */
        const val LINE_HEIGHT = 11

        /** 牌面寬度。 */
        const val TILE_WIDTH = 18

        /** 牌面高度。 */
        const val TILE_HEIGHT = 24

        /** 牌面起點間距。 */
        const val TILE_STEP = 20

        /** 一般文字。 */
        const val TEXT_COLOR = 0xffffff

        /** 次要文字。 */
        const val MUTED_COLOR = 0xaaaaaa

        /** 重要資訊。 */
        const val ACCENT_COLOR = 0x8ed5df

        /** 失敗訊息。 */
        const val ERROR_COLOR = 0xff6666

        /** 一般卡片。 */
        val CARD_COLOR = 0xB0202020.toInt()

        /** 指向卡片。 */
        val CARD_HOVER_COLOR = 0xB0343434.toInt()
    }
}
