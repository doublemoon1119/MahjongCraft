package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchDetailDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** 顯示單場歷史對局的安全摘要與局級索引。
 *
 * @property session 共用歷史瀏覽 session。
 */
internal class HistorySummaryScreen(
    private val session: HistoryBrowseSession,
) : Screen(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_TITLE)) {
    /** 目前摘要幾何。 */
    private var layout = HistorySummaryLayout.measure(0, 0)

    /** 目前內容捲動偏移。 */
    private var scroll = 0.0

    /** 捲軸是否正在拖曳。 */
    private var dragging = false

    /** 捲軸拖曳時游標相對滑塊上界的偏移。 */
    private var grabOffset = 0.0

    /** 返回按鈕；只有它顯示時置中。 */
    private var backButton: ButtonWidget? = null

    /** 重試按鈕。 */
    private var retryButton: ButtonWidget? = null

    /** 複製成功提示的單調時間起點。 */
    private var copiedAt: TimeMark? = null

    /** 建立固定 footer 與摘要內容的版面控制項。 */
    override fun init() {
        layout = HistorySummaryLayout.measure(width, height)
        scroll = session.controller.state.value.summary?.scrollOffset ?: 0.0
        dragging = false
        clearChildren()
        val (backBounds, retryBounds) = layout.footerButtons(width, secondVisible = true)
        backButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.BACK)) { session.backToList() }
            .dimensions(backBounds.x, backBounds.y, backBounds.width, backBounds.height).build().also(::addDrawableChild)
        retryButton = ButtonWidget.builder(Text.translatable(MinecraftHistoryScreenKeys.RETRY)) { if (session.controller.canRetry()) session.controller.retry() }
            .dimensions(checkNotNull(retryBounds).x, retryBounds.y, retryBounds.width, retryBounds.height).build().also(::addDrawableChild)
    }

    /**
     * 依重試按鈕是否顯示調整底部按鈕位置：只有返回按鈕時置中，兩顆時並排置中。
     *
     * @param retryVisible 是否顯示重試按鈕。
     */
    private fun placeFooter(retryVisible: Boolean) {
        val (back, retry) = layout.footerButtons(width, secondVisible = retryVisible)
        backButton?.x = back.x
        retry?.let { retryButton?.x = it.x }
    }

    /** Minecraft 移除此畫面時通知 session。 */
    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /** Escape 與關閉行為均返回列表。 */
    override fun close() = session.backToList()

    /** 摘要與其他歷史畫面一致，不暫停整合伺服器。 */
    override fun shouldPause(): Boolean = false

    /** 處理內容區滾輪。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param amount 滾輪增量。
     * @return 是否處理此輸入。
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (mouseY < layout.contentTop || mouseY >= layout.contentBottom) return super.mouseScrolled(mouseX, mouseY, amount)
        scroll = layout.clampScroll(scroll - amount * 18.0, contentHeight(detail()))
        session.controller.rememberSummaryPosition(scroll)
        return true
    }

    /** 開始捲軸拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @return 是否處理此輸入。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val bar = layout.scrollbar(contentHeight(detail()), scroll)
        val bounds = layout.scrollbarBounds()
        if (button == 0 && bar.maximumScroll > 0 && mouseX >= bounds.x && mouseX < bounds.x + bounds.width && mouseY >= bounds.y && mouseY < bounds.y + bounds.height) {
            dragging = true
            grabOffset = layout.grabOffset(contentHeight(detail()), scroll, mouseY)
            updateDrag(mouseY)
            return true
        }
        val detail = detail()
        if (button == 0 && !dragging && detail != null) {
            val offset = layout.clampScroll(scroll, contentHeight(detail)).toInt()
            if (buildLines(detail).any { it.copyMatchId && layout.containsContentRow(mouseX, mouseY, it.x, it.y - offset, 11) }) {
                MinecraftClient.getInstance().keyboard.clipboard = detail.summary.matchId
                copiedAt = TimeSource.Monotonic.markNow()
                return true
            }
            if (buildLines(detail).any { it.openRuleSettings && layout.containsContentRow(mouseX, mouseY, it.x, it.y - offset, 11) }) {
                session.openRuleSettings()
                return true
            }
            val links = buildLines(detail).mapNotNull { line -> line.roundNumber?.let { HistorySummaryLayout.RoundLink(line.x, line.y, it) } }
            layout.roundAt(mouseX, mouseY, offset.toDouble(), links)?.let { roundNumber ->
                session.openRound(roundNumber)
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /** 更新拖曳捲動位置。
     *
     * @param mouseY 游標垂直座標。
     */
    private fun updateDrag(mouseY: Double) {
        scroll = layout.scrollbar(contentHeight(detail()), scroll).scrollIndexFor(mouseY, grabOffset).toDouble()
        session.controller.rememberSummaryPosition(scroll)
    }

    /** 持續處理捲軸拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @param deltaX 水平位移。
     * @param deltaY 垂直位移。
     * @return 是否處理此輸入。
     */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = if (dragging && button == 0) {
        updateDrag(mouseY)
        true
    } else {
        super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    /** 結束捲軸拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @return 是否處理此輸入。
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = dragging && button == 0
        dragging = false
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    /** 繪製摘要畫面。 */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 2, 0xffffff)
        val summary = session.controller.state.value.summary
        val status = summary?.status ?: HistoryBrowseStatus.Loading
        scroll = layout.clampScroll(scroll, contentHeight(detail()))
        var hoveredTooltip: List<Text>? = null
        retryButton?.active = session.controller.canRetry()
        val retryVisible = status is HistoryBrowseStatus.Failed
        retryButton?.visible = retryVisible
        placeFooter(retryVisible)
        retryButton?.tooltip = if (session.controller.isRefreshCoolingDown()) Tooltip.of(Text.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP)) else null
        context.enableScissor(8, layout.contentTop, width - 8, layout.contentBottom)
        when (status) {
            HistoryBrowseStatus.Idle, HistoryBrowseStatus.Loading -> centered(context, Text.translatable(MinecraftHistoryScreenKeys.LOADING), layout.contentTop + 12, 0xffffff)
            is HistoryBrowseStatus.Failed -> centered(context, Text.translatable("${MinecraftHistoryScreenKeys.FAILURE_PREFIX}${status.reason.name.lowercase()}"), layout.contentTop + 12, 0xff6666)
            HistoryBrowseStatus.Ready -> summary?.detail?.let { hoveredTooltip = renderDetail(context, it, mouseX, mouseY) }
        }
        context.disableScissor()
        scroll = layout.clampScroll(scroll, contentHeight(detail()))
        renderScrollbar(context, contentHeight(detail()))
        super.render(context, mouseX, mouseY, delta)
        hoveredTooltip?.let { context.drawTooltip(textRenderer, it, mouseX, mouseY) }
    }

    /**
     * 繪製成功取得的摘要內容。
     *
     * @param context 繪製上下文。
     * @param detail 權威摘要。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 實際指向且可見資訊的 tooltip。
     */
    private fun renderDetail(context: DrawContext, detail: HistoryMatchDetailDto, mouseX: Int, mouseY: Int): List<Text>? {
        val lines = buildLines(detail)
        val contentHeight = contentHeight(detail)
        val offset = layout.clampScroll(scroll, contentHeight).toInt()
        val faceX = layout.left + 8 + rankColumnWidth(detail)
        val nameX = faceX + 14
        var tooltip: List<Text>? = null
        lines.forEach { line ->
            val y = line.y - offset
            if (y + 10 >= layout.contentTop && y <= layout.contentBottom) {
                line.participant?.let { row ->
                    session.participants.render(row.participant, context, faceX, y - 1, 10)
                    context.drawTextWithShadow(textRenderer, row.rank, layout.left + 8, y, 0xcccccc)
                    val scoreRight = layout.right - 14
                    val nameWidth = (scoreRight - textRenderer.getWidth(row.score) - 12 - nameX).coerceAtLeast(1)
                    val name = HistoryScreenText.trim(row.name.string, nameWidth, textRenderer::getWidth)
                    context.drawTextWithShadow(textRenderer, Text.literal(name), nameX, y, row.nameColor)
                    context.drawTextWithShadow(textRenderer, row.score, scoreRight - textRenderer.getWidth(row.score), y, 0xdddddd)
                    if (name != row.name.string && !dragging && layout.containsContentRow(mouseX.toDouble(), mouseY.toDouble(), nameX, y, 11)) tooltip = listOf(row.name)
                } ?: context.drawTextWithShadow(textRenderer, line.text, line.x, y, if ((line.copyMatchId || line.openRuleSettings || line.roundNumber != null) && !dragging && layout.containsContentRow(mouseX.toDouble(), mouseY.toDouble(), line.x, y, 11)) 0x8ed5df else line.color)
                if (line.tooltip != null && !dragging && layout.containsContentRow(mouseX.toDouble(), mouseY.toDouble(), line.x, y, 11)) {
                    tooltip = if (line.copyMatchId) {
                        val copied = copiedAt?.elapsedNow()?.let { it < 2.seconds } == true
                        listOf(line.tooltip, Text.translatable(if (copied) MinecraftHistoryScreenKeys.SUMMARY_MATCH_ID_COPIED else MinecraftHistoryScreenKeys.SUMMARY_COPY_MATCH_ID).formatted(if (copied) Formatting.GREEN else Formatting.AQUA))
                    } else {
                        listOf(line.tooltip)
                    }
                }
            }
        }
        return tooltip
    }

    /**
     * 建立依實際字寬換行的摘要內容，不假設固定玩家數。
     *
     * @param detail 權威摘要與局索引。
     * @return 含排名與資訊的有序繪製行。
     */
    private fun buildLines(detail: HistoryMatchDetailDto): List<Line> {
        val summary = detail.summary
        val lines = mutableListOf<Line>()
        var y = layout.contentTop + 4

        /**
         * 按可用字寬加入資訊行，並附上可選完整值提示。
         *
         * @param text 完整資訊文字。
         * @param color 行文字色。
         * @param x 行左界。
         * @param tooltip 可選的完整值或說明。
         * @param copyMatchId 是否可複製對局 ID。
         * @param openRuleSettings 是否可開啟唯讀歷史規則設定。
         * @param roundNumber 可開啟的局序號；其他資訊行為 null。
         */
        fun add(text: Text, color: Int = 0xffffff, x: Int = layout.left + 8, tooltip: Text? = null, copyMatchId: Boolean = false, openRuleSettings: Boolean = false, roundNumber: Int? = null) {
            val wrapped = textRenderer.wrapLines(text, (layout.right - x - 14).coerceAtLeast(1))
            wrapped.forEach { line ->
                lines += Line(x, y, line, color, tooltip = tooltip, copyMatchId = copyMatchId, openRuleSettings = openRuleSettings, roundNumber = roundNumber)
                y += 11
            }
        }
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_BASIC), 0x8ed5df, layout.left)
        y += 4
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_MATCH_ID).append(": ").append(summary.matchId), tooltip = Text.literal(summary.matchId), copyMatchId = true)
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_RULE).copy().append(": ").append(HistoryScreenText.rule(summary.ruleId, session.ruleNames)), tooltip = Text.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_OPEN_HINT).formatted(Formatting.AQUA), openRuleSettings = true)
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_STARTED_AT).append(": ").append(HistoryScreenText.endedAt(summary.startedAtEpochMillis)))
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_ENDED_AT).append(": ").append(HistoryScreenText.endedAt(summary.endedAtEpochMillis)))
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_DURATION).append(": ").append(HistoryScreenText.duration(summary.durationMillis)))
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_OUTCOME).copy().append(": ").append(HistoryScreenText.outcome(summary.outcome)))
        val integrityKey = if (summary.integrity == HistoryIntegrityFilterDto.COMPLETE) MinecraftHistoryScreenKeys.INTEGRITY_COMPLETE else MinecraftHistoryScreenKeys.INTEGRITY_INCOMPLETE
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_INTEGRITY).copy().append(": ").append(Text.translatable(integrityKey)), tooltip = Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_INTEGRITY_TOOLTIP, Text.translatable(integrityKey)))
        y += 8
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_RANKING), 0x8ed5df, layout.left)
        y += 4
        val headerY = y
        val rankWidth = rankColumnWidth(detail)
        val nameX = layout.left + 8 + rankWidth + 14
        val scoreHeader = Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_SCORE_HEADER)
        val playerWidth = (layout.right - 14 - textRenderer.getWidth(scoreHeader) - 12 - nameX).coerceAtLeast(1)
        lines += Line(layout.left + 8, headerY, Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_RANK_HEADER).asOrderedText(), 0xcccccc)
        lines += Line(nameX, headerY, Text.literal(HistoryScreenText.trim(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_PLAYER_HEADER).string, playerWidth, textRenderer::getWidth)).asOrderedText(), 0xcccccc)
        lines += Line(layout.right - 14 - textRenderer.getWidth(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_SCORE_HEADER)), headerY, Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_SCORE_HEADER).asOrderedText(), 0xcccccc)
        y += 11
        val results = summary.results.associateBy { it.playerId }
        val ownId = MinecraftClient.getInstance().player?.uuid?.toString()
        HistoryScreenText.rankedParticipants(summary).forEach { participant ->
            val result = results[participant.playerId]
            val name = session.participants.name(participant, summary.participants)
            val rank = Text.literal(result?.finalRank?.toString() ?: "—")
            val score = Text.literal(result?.finalScore?.toString() ?: "—")
            lines += Line(layout.left, y, Text.empty().asOrderedText(), 0xffffff, ParticipantRow(participant, rank, name, score, if (participant.playerId == ownId) 0x8ed5df else 0xffffff))
            y += 14
        }
        if (!summary.resultsAvailable || summary.results.isEmpty()) add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_RESULTS_UNAVAILABLE), 0xcccccc)
        y += 8
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_ROUND_INDEX), 0x8ed5df, layout.left)
        y += 4
        add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_ROUND_COUNT).append(": ").append(summary.roundCount?.toString() ?: "—"))
        if (detail.rounds.isEmpty()) {
            add(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_ROUNDS_EMPTY), 0xcccccc)
        } else {
            detail.rounds.sortedBy { it.roundNumber }.forEach { round ->
                val duration = HistoryScreenText.intervalDuration(round.startedAtEpochMillis, round.endedAtEpochMillis)
                add(
                    Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_ROUND_ROW, round.roundNumber, HistoryScreenText.endedAt(round.startedAtEpochMillis), HistoryScreenText.endedAt(round.endedAtEpochMillis), duration),
                    tooltip = Text.translatable(MinecraftHistoryScreenKeys.ROUND_OPEN_HINT).formatted(Formatting.AQUA),
                    roundNumber = round.roundNumber,
                )
            }
        }
        return lines
    }

    /**
     * 以名次標題及權威數值的實際字寬決定排名欄寬。
     *
     * @param detail 權威摘要。
     * @return 含欄間距的像素寬度。
     */
    private fun rankColumnWidth(detail: HistoryMatchDetailDto): Int = maxOf(
        textRenderer.getWidth(Text.translatable(MinecraftHistoryScreenKeys.SUMMARY_RANK_HEADER)),
        detail.summary.results.maxOfOrNull { textRenderer.getWidth(it.finalRank?.toString() ?: "—") } ?: textRenderer.getWidth("—"),
    ) + 8

    /**
     * 計算摘要總高度，與繪製列數保持一致。
     *
     * @param detail 成功摘要；null 表示尚無內容。
     * @return 含首尾間距的總像素高度。
     */
    private fun contentHeight(detail: HistoryMatchDetailDto?): Int = detail?.let {
        val lines = buildLines(it)
        (lines.maxOfOrNull { line -> line.y - layout.contentTop + if (line.participant == null) 11 else 14 } ?: 0).plus(4).coerceAtLeast(layout.viewportHeight)
    } ?: layout.viewportHeight

    /**
     * 繪製與拖曳共用幾何的捲軸。
     *
     * @param context 繪製上下文。
     * @param height 內容總高度。
     */
    private fun renderScrollbar(context: DrawContext, height: Int) {
        val bar = layout.scrollbar(height, scroll)
        if (bar.maximumScroll <= 0 || layout.contentBottom <= layout.contentTop) return
        val bounds = layout.scrollbarBounds()
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0x80505050.toInt())
        context.fill(bounds.x, bar.thumbTop, bounds.x + bounds.width, bar.thumbTop + bar.thumbHeight, 0xFFD0D0D0.toInt())
    }

    /** 取得目前成功摘要，不使用失敗時殘留的舊內容計算捲軸。 */
    private fun detail(): HistoryMatchDetailDto? = session.controller.state.value.summary?.takeIf { it.status == HistoryBrowseStatus.Ready }?.detail

    /**
     * 依可用字寬顯示安全的載入或失敗訊息。
     *
     * @param context 繪製上下文。
     * @param text 已翻譯的訊息。
     * @param y 文字上界。
     * @param color 文字顏色。
     */
    private fun centered(context: DrawContext, text: Text, y: Int, color: Int) {
        textRenderer.wrapLines(text, (layout.right - layout.left - 14).coerceAtLeast(1)).forEachIndexed { index, line ->
            context.drawTextWithShadow(textRenderer, line, layout.left, y + index * 11, color)
        }
    }

    /**
     * 摘要畫面的一行繪製資料。
     *
     * @property x 行左界。
     * @property y 未捲動的上界。
     * @property text 已換行文字。
     * @property color 行顏色。
     * @property participant 可選排名列。
     * @property tooltip 可見行的完整值或用途說明。
     * @property copyMatchId 此行是否提供對局 ID 複製。
     * @property openRuleSettings 此行是否開啟規則設定。
     * @property roundNumber 此行可開啟的局序號；其他資訊行為 null。
     */
    private data class Line(
        val x: Int,
        val y: Int,
        val text: OrderedText,
        val color: Int,
        val participant: ParticipantRow? = null,
        val tooltip: Text? = null,
        val copyMatchId: Boolean = false,
        val openRuleSettings: Boolean = false,
        val roundNumber: Int? = null,
    )

    /**
     * 排名欄位的單一資料列。
     *
     * @property participant 參與者資料。
     * @property rank 權威名次或未知佔位。
     * @property name 共用來源解析的名稱。
     * @property score 權威分數或未知佔位。
     * @property nameColor 自己的名稱提示色；不改變其他欄位顏色。
     */
    private data class ParticipantRow(
        val participant: HistoryParticipantSummaryDto,
        val rank: Text,
        val name: Text,
        val score: Text,
        val nameColor: Int,
    )
}
