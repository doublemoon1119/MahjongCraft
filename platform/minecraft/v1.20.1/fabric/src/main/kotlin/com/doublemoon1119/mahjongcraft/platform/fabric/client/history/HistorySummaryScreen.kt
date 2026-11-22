package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryIntegrityFilterDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryMatchDetailDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ControlTooltip
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelIcon
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelIcons
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RowStripe
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryBrowseStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryPageFrame
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistorySummaryLayout
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 顯示單場牌譜的基本資訊、排名與各局列表。
 *
 * 基本資訊為名稱與值兩欄對齊，對局 ID 旁有複製按鈕、規則旁有查看規則設定的按鈕；每局一列，點整列開啟該局紀錄。
 *
 * @property session 共用歷史瀏覽 session。
 */
internal class HistorySummaryScreen(
    private val session: HistoryBrowseSession,
) : Screen(Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_TITLE)) {
    /** 目前摘要幾何。 */
    private var layout = HistorySummaryLayout.measure(0, 0)

    /** 目前內容捲動偏移。 */
    private var scroll = 0.0

    /** 捲軸是否正在拖曳。 */
    private var dragging = false

    /** 捲軸拖曳時游標相對滑塊上界的偏移。 */
    private var grabOffset = 0.0

    /** 重試按鈕。 */
    private var retryButton: Button? = null

    /** 對局 ID 旁的複製按鈕；隨內容捲動，由本畫面在內容裁切範圍內繪製。 */
    private lateinit var copyButton: RestartableMarqueeButtonWidget

    /** 規則旁查看規則設定的按鈕；隨內容捲動，由本畫面在內容裁切範圍內繪製。 */
    private lateinit var ruleSettingsButton: RestartableMarqueeButtonWidget

    /** 各局列的局數欄寬度；建立列時依實際文字更新。 */
    private var roundLabelWidth = 0

    /** 複製成功提示的單調時間起點。 */
    private var copiedAt: TimeMark? = null

    /** 建立固定 footer。 */
    override fun init() {
        layout = HistorySummaryLayout.measure(width, height)
        scroll = session.controller.state.value.summary?.scrollOffset ?: 0.0
        dragging = false
        clearWidgets()
        val retry = layout.footerSlot(HistoryPageFrame.LAST_SLOT - 1)
        retryButton = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.RETRY)) { if (session.controller.canRetry()) session.controller.retry() }
                .dimensions(retry.x, retry.y, retry.width, retry.height)
                .build(),
        )
        val back = layout.footerSlot(HistoryPageFrame.LAST_SLOT)
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftHistoryScreenKeys.BACK)) { session.backToList() }
                .dimensions(back.x, back.y, back.width, back.height)
                .build(),
        )
        copyButton = miniButton(Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_COPY)) { copyMatchId() }
        ruleSettingsButton = miniButton(Component.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_TITLE)) { session.openRuleSettings() }.also {
            it.tooltip = Tooltip.create(ControlTooltip.description(Component.translatable(MinecraftHistoryScreenKeys.RULE_SETTINGS_OPEN_HINT)))
        }
    }

    /**
     * 建立列上的小按鈕；寬度依文字決定，位置由 [placeContentButtons] 決定。不加入畫面元件，由本畫面在內容裁切範圍內繪製與處理點擊。
     *
     * @param label 按鈕文字。
     * @param action 按下後的行為。
     * @return 建立的按鈕。
     */
    private fun miniButton(label: Component, action: () -> Unit): RestartableMarqueeButtonWidget = addWidget(
        RestartableMarqueeButtonWidget.builder(label) { action() }
            .dimensions(0, 0, font.width(label) + MINI_BUTTON_PADDING * 2, MINI_BUTTON_HEIGHT)
            .build().also {
                it.visible = false
                it.clickClip = { x, y -> layout.containsContent(x, y) }
            },
    )

    /** Minecraft 移除此畫面時通知 session。 */
    override fun removed() {
        session.removed(this)
        super.removed()
    }

    /** Escape 與關閉行為均返回列表。 */
    override fun onClose() = session.backToList()

    /** 摘要與其他歷史畫面一致，不暫停整合伺服器。 */
    override fun isPauseScreen(): Boolean = false

    /** 將對局 ID 複製到剪貼簿，並在複製按鈕的提示中短暫顯示已複製。 */
    private fun copyMatchId() {
        val detail = detail() ?: return
        Minecraft.getInstance().keyboardHandler.clipboard = detail.summary.matchId
        copiedAt = TimeSource.Monotonic.markNow()
    }

    /**
     * 處理內容區滾輪。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param amount 滾輪增量。
     * @return 是否處理此輸入。
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (mouseY < layout.contentTop || mouseY >= layout.contentBottom) return super.mouseScrolled(mouseX, mouseY, amount)
        scroll = layout.clampScroll(scroll - amount * SCROLL_STEP, contentHeight(detail()))
        session.controller.rememberSummaryPosition(scroll)
        return true
    }

    /**
     * 開始捲軸拖曳，或點擊內容區內的按鈕與局列。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @return 是否處理此輸入。
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val detail = detail()
        val bar = layout.scrollbar(contentHeight(detail), scroll)
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && bar.maximumScroll > 0 && layout.isOverScrollbar(mouseX, mouseY)) {
            dragging = true
            grabOffset = layout.grabOffset(contentHeight(detail), scroll, mouseY)
            updateDrag(mouseY)
            return true
        }
        if (detail != null && layout.containsContent(mouseX, mouseY)) {
            placeContentButtons(detail)
            if (copyButton.mouseClicked(mouseX, mouseY, button) || ruleSettingsButton.mouseClicked(mouseX, mouseY, button)) return true
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                val offset = scrollOffset(detail)
                rows(detail).filterIsInstance<RoundRow>().firstOrNull { layout.containsRow(mouseX, mouseY, rowTop(it, offset), it.height) }?.let { row ->
                    playClickSound()
                    session.openRound(row.roundNumber)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /** 播放與按鈕相同的點擊音效。 */
    private fun playClickSound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
    }

    /**
     * 更新拖曳捲動位置。
     *
     * @param mouseY 游標垂直座標。
     */
    private fun updateDrag(mouseY: Double) {
        scroll = layout.scrollbar(contentHeight(detail()), scroll).scrollIndexFor(mouseY, grabOffset).toDouble()
        session.controller.rememberSummaryPosition(scroll)
    }

    /**
     * 持續處理捲軸拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @param deltaX 水平位移。
     * @param deltaY 垂直位移。
     * @return 是否處理此輸入。
     */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        updateDrag(mouseY)
        true
    } else {
        super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    /**
     * 結束捲軸拖曳。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param button 滑鼠按鍵。
     * @return 是否處理此輸入。
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val handled = dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
        dragging = false
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    /** 繪製摘要畫面。 */
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        ScreenRendering.title(context, font, title, width)
        val summary = session.controller.state.value.summary
        val status = summary?.status ?: HistoryBrowseStatus.Loading
        scroll = layout.clampScroll(scroll, contentHeight(detail()))
        copyButton.visible = false
        ruleSettingsButton.visible = false
        var hoveredTooltip: List<Component>? = null
        retryButton?.active = session.controller.canRetry()
        retryButton?.visible = status is HistoryBrowseStatus.Failed
        retryButton?.tooltip = if (session.controller.isRefreshCoolingDown()) Tooltip.create(Component.translatable(MinecraftHistoryScreenKeys.QUERY_COOLDOWN_TOOLTIP)) else null
        context.enableScissor(layout.left, layout.contentTop, layout.right, layout.contentBottom)
        when (status) {
            HistoryBrowseStatus.Idle, HistoryBrowseStatus.Loading -> message(context, Component.translatable(MinecraftHistoryScreenKeys.LOADING), STATUS_COLOR)
            is HistoryBrowseStatus.Failed -> message(context, Component.translatable(status.reason.translationKey), ERROR_COLOR)
            HistoryBrowseStatus.Ready -> summary?.detail?.let { hoveredTooltip = renderDetail(context, it, mouseX, mouseY, delta) }
        }
        context.disableScissor()
        renderScrollbar(context, contentHeight(detail()))
        super.render(context, mouseX, mouseY, delta)
        hoveredTooltip?.let { context.renderComponentTooltip(font, it, mouseX, mouseY) }
    }

    /**
     * 繪製成功取得的摘要內容。
     *
     * @param context 繪製上下文。
     * @param detail 權威摘要。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param delta 畫面更新比例。
     * @return 游標所指且可見內容的提示。
     */
    private fun renderDetail(context: GuiGraphics, detail: HistoryMatchDetailDto, mouseX: Int, mouseY: Int, delta: Float): List<Component>? {
        val offset = scrollOffset(detail)
        val pointerInside = !dragging && layout.containsContent(mouseX.toDouble(), mouseY.toDouble())
        val keyWidth = keyColumnWidth(detail)
        val rankWidth = rankColumnWidth(detail)
        var tooltip: List<Component>? = null
        rows(detail).forEach { row ->
            val top = rowTop(row, offset)
            if (top + row.height < layout.contentTop || top > layout.contentBottom) return@forEach
            val hovered = pointerInside && layout.containsRow(mouseX.toDouble(), mouseY.toDouble(), top, row.height)
            when (row) {
                is HeadingRow -> context.drawString(font, row.text, layout.left, top + 2, HEADING_COLOR)
                is FieldRow -> tooltip = renderField(context, row, top, keyWidth, hovered) ?: tooltip
                is RankHeaderRow -> renderRankHeader(context, top, rankWidth)
                is ParticipantRow -> tooltip = renderParticipant(context, row, top, rankWidth, hovered, mouseX) ?: tooltip
                is NoteRow -> context.drawString(font, row.text, layout.left + ROW_INSET, top + 2, NOTE_COLOR)
                is RoundRow -> {
                    renderRound(context, row, top, hovered)
                    if (hovered) tooltip = listOf(Component.translatable(MinecraftHistoryScreenKeys.ROUND_OPEN_HINT).withStyle(ChatFormatting.GRAY))
                }
            }
        }
        placeContentButtons(detail)
        val pointerX = if (pointerInside) mouseX else OFFSCREEN_POINTER
        val pointerY = if (pointerInside) mouseY else OFFSCREEN_POINTER
        val copied = copiedAt?.elapsedNow()?.let { it < COPIED_NOTICE } == true
        copyButton.tooltip = Tooltip.create(
            if (copied) {
                Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_MATCH_ID_COPIED).withStyle(ChatFormatting.GREEN)
            } else {
                Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_COPY_MATCH_ID).withStyle(ChatFormatting.GRAY)
            },
        )
        copyButton.render(context, pointerX, pointerY, delta)
        ruleSettingsButton.render(context, pointerX, pointerY, delta)
        return tooltip
    }

    /**
     * 繪製基本資訊的一列：名稱在左欄、值在右欄，有按鈕時值讓出按鈕的寬度。
     *
     * @param context 繪製上下文。
     * @param row 要繪製的列。
     * @param top 捲動後的列上界。
     * @param keyWidth 名稱欄寬度。
     * @param hovered 游標是否在這一列上。
     * @return 游標所指時的提示；沒有時為 null。
     */
    private fun renderField(context: GuiGraphics, row: FieldRow, top: Int, keyWidth: Int, hovered: Boolean): List<Component>? {
        stripe(top, row.height).renderIfStriped(context, row.stripeIndex)
        val textY = top + (row.height - VISIBLE_TEXT_HEIGHT) / 2
        context.drawString(font, row.key, layout.left + ROW_INSET, textY, KEY_COLOR)
        val valueLeft = layout.left + ROW_INSET + keyWidth
        val valueRight = layout.right - ROW_INSET - (actionButton(row.action)?.let { it.width + BUTTON_GAP } ?: 0)
        val shown = HistoryScreenText.trim(row.value.string, (valueRight - valueLeft).coerceAtLeast(1), font::width)
        context.drawString(font, Component.literal(shown), valueLeft, textY, VALUE_COLOR)
        if (!hovered) return null
        return when {
            row.tooltip != null -> row.tooltip
            shown != row.value.string -> listOf(row.value)
            else -> null
        }
    }

    /**
     * 繪製排名表的欄位標題。
     *
     * @param context 繪製上下文。
     * @param top 捲動後的列上界。
     * @param rankWidth 名次欄寬度。
     */
    private fun renderRankHeader(context: GuiGraphics, top: Int, rankWidth: Int) {
        val y = top + 2
        context.drawString(font, Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_RANK_HEADER), layout.left + ROW_INSET, y, KEY_COLOR)
        context.drawString(font, Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_PLAYER_HEADER), layout.left + ROW_INSET + rankWidth + FACE_WIDTH, y, KEY_COLOR)
        val score = Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_SCORE_HEADER)
        context.drawString(font, score, layout.right - ROW_INSET - font.width(score), y, KEY_COLOR)
    }

    /**
     * 繪製排名表的一列：名次、頭像、名稱與分數。
     *
     * @param context 繪製上下文。
     * @param row 要繪製的列。
     * @param top 捲動後的列上界。
     * @param rankWidth 名次欄寬度。
     * @param hovered 游標是否在這一列上。
     * @param mouseX 游標水平座標。
     * @return 名稱被截斷且游標停在名稱上時的完整名稱；沒有時為 null。
     */
    private fun renderParticipant(context: GuiGraphics, row: ParticipantRow, top: Int, rankWidth: Int, hovered: Boolean, mouseX: Int): List<Component>? {
        stripe(top, row.height).renderIfStriped(context, row.stripeIndex)
        val textY = top + (row.height - VISIBLE_TEXT_HEIGHT) / 2
        val faceX = layout.left + ROW_INSET + rankWidth
        val nameX = faceX + FACE_WIDTH
        context.drawString(font, row.rank, layout.left + ROW_INSET, textY, KEY_COLOR)
        session.participants.render(row.participant, context, faceX, textY - 1, FACE_SIZE, matchId = detail()?.summary?.matchId)
        val scoreRight = layout.right - ROW_INSET
        val nameWidth = (scoreRight - font.width(row.score) - COLUMN_GAP - nameX).coerceAtLeast(1)
        val name = HistoryScreenText.trim(row.name.string, nameWidth, font::width)
        context.drawString(font, Component.literal(name), nameX, textY, row.nameColor)
        context.drawString(font, row.score, scoreRight - font.width(row.score), textY, VALUE_COLOR)
        return if (hovered && name != row.name.string && mouseX >= nameX && mouseX < nameX + nameWidth) listOf(row.name) else null
    }

    /**
     * 繪製一局的列：卡片底色，游標停留時加亮並加白框；右端畫進入箭頭。
     *
     * @param context 繪製上下文。
     * @param row 要繪製的列。
     * @param top 捲動後的列上界。
     * @param hovered 游標是否在這一列上。
     */
    private fun renderRound(context: GuiGraphics, row: RoundRow, top: Int, hovered: Boolean) {
        val bottom = top + row.height - ROUND_GAP
        context.fill(layout.left, top, layout.right, bottom, if (hovered) ROUND_HOVER_BACKGROUND else ROUND_BACKGROUND)
        if (hovered) context.renderOutline(layout.left, top, layout.right - layout.left, bottom - top, OUTLINE_COLOR)
        val textY = top + (bottom - top - VISIBLE_TEXT_HEIGHT) / 2
        context.drawString(font, row.label, layout.left + ROW_INSET, textY, VALUE_COLOR)
        val timesLeft = layout.left + ROW_INSET + roundLabelWidth
        val arrowX = layout.right - ROW_INSET - PixelIcon.OPEN_RIGHT.width
        val times = HistoryScreenText.trim(row.times.string, (arrowX - COLUMN_GAP - timesLeft).coerceAtLeast(1), font::width)
        context.drawString(font, Component.literal(times), timesLeft, textY, NOTE_COLOR)
        PixelIcons.icon(context, PixelIcon.OPEN_RIGHT, arrowX, top + (bottom - top - PixelIcon.OPEN_RIGHT.height) / 2, VALUE_COLOR)
    }

    /**
     * 將對局 ID 與規則兩列的按鈕放到列的右端；所在列捲出內容區時隱藏。
     *
     * @param detail 權威摘要。
     */
    private fun placeContentButtons(detail: HistoryMatchDetailDto) {
        val offset = scrollOffset(detail)
        listOf(FieldAction.COPY_MATCH_ID to copyButton, FieldAction.RULE_SETTINGS to ruleSettingsButton).forEach { (action, button) ->
            val row = rows(detail).filterIsInstance<FieldRow>().firstOrNull { it.action == action }
            if (row == null) {
                button.visible = false
                return@forEach
            }
            val top = rowTop(row, offset)
            button.x = layout.right - ROW_INSET - button.width
            button.y = top + (row.height - button.height) / 2
            button.visible = button.y + button.height > layout.contentTop && button.y < layout.contentBottom
        }
    }

    /**
     * 取得列上的按鈕。
     *
     * @param action 列的按鈕種類；沒有按鈕時為 null。
     * @return 對應的按鈕；沒有按鈕時為 null。
     */
    private fun actionButton(action: FieldAction?): RestartableMarqueeButtonWidget? = when (action) {
        FieldAction.COPY_MATCH_ID -> copyButton
        FieldAction.RULE_SETTINGS -> ruleSettingsButton
        null -> null
    }

    /**
     * 建立隔列底色的範圍：橫跨內容寬度。
     *
     * @param top 捲動後的列上界。
     * @param height 列高度。
     * @return 底色範圍。
     */
    private fun stripe(top: Int, height: Int): RowStripe = RowStripe(left = layout.left, top = top, right = layout.right, bottom = top + height)

    /**
     * 依序建立摘要的每一列，不假設固定玩家數。
     *
     * @param detail 權威摘要與各局列表。
     * @return 由上到下排列、上界相對內容區頂端的列。
     */
    private fun rows(detail: HistoryMatchDetailDto): List<SummaryRow> {
        val summary = detail.summary
        val rows = mutableListOf<SummaryRow>()
        var top = 0

        /**
         * 加入一列並往下推進。
         *
         * @param row 要加入的列。
         */
        fun add(row: SummaryRow) {
            rows += row
            top += row.height
        }

        /**
         * 加入一段標題；不是第一段時上方先留白。
         *
         * @param key 標題翻譯鍵。
         */
        fun heading(key: String) {
            if (rows.isNotEmpty()) top += SECTION_GAP
            add(HeadingRow(top = top, text = Component.translatable(key)))
        }

        /**
         * 加入可能換行的說明文字。
         *
         * @param text 說明文字。
         */
        fun note(text: Component) {
            font.split(text, (layout.right - layout.left - ROW_INSET * 2).coerceAtLeast(1)).forEach { line ->
                add(NoteRow(top = top, text = line))
            }
        }

        heading(MinecraftHistoryScreenKeys.SUMMARY_BASIC)
        val integrityKey = if (summary.integrity == HistoryIntegrityFilterDto.COMPLETE) MinecraftHistoryScreenKeys.INTEGRITY_COMPLETE else MinecraftHistoryScreenKeys.INTEGRITY_INCOMPLETE
        val fields = listOf(
            FieldContent(MinecraftHistoryScreenKeys.SUMMARY_MATCH_ID, Component.literal(summary.matchId), action = FieldAction.COPY_MATCH_ID),
            FieldContent(MinecraftHistoryScreenKeys.SUMMARY_RULE, HistoryScreenText.rule(summary.ruleId, session.ruleNames), action = FieldAction.RULE_SETTINGS),
            FieldContent(MinecraftHistoryScreenKeys.SUMMARY_STARTED_AT, Component.literal(HistoryScreenText.endedAt(summary.startedAtEpochMillis))),
            FieldContent(MinecraftHistoryScreenKeys.SUMMARY_ENDED_AT, Component.literal(HistoryScreenText.endedAt(summary.endedAtEpochMillis))),
            FieldContent(MinecraftHistoryScreenKeys.SUMMARY_DURATION, Component.literal(HistoryScreenText.duration(summary.duration))),
            FieldContent(
                keyId = MinecraftHistoryScreenKeys.SUMMARY_OUTCOME,
                value = HistoryScreenText.outcome(summary.outcome).copy().append(VALUE_SEPARATOR).append(Component.translatable(integrityKey)),
                tooltip = listOf(Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_INTEGRITY_TOOLTIP, Component.translatable(integrityKey))),
            ),
        )
        fields.forEachIndexed { index, field ->
            add(FieldRow(top = top, stripeIndex = index, key = Component.translatable(field.keyId), value = field.value, tooltip = field.tooltip, action = field.action))
        }
        heading(MinecraftHistoryScreenKeys.SUMMARY_RANKING)
        add(RankHeaderRow(top = top))
        val results = summary.results.associateBy { it.playerId }
        val ownId = Minecraft.getInstance().player?.uuid?.toString()
        HistoryScreenText.rankedParticipants(summary).forEachIndexed { index, participant ->
            val result = results[participant.playerId]
            add(
                row = ParticipantRow(
                    top = top,
                    stripeIndex = index,
                    participant = participant,
                    rank = Component.literal(result?.finalRank?.toString() ?: "—"),
                    name = session.participants.name(participant, summary.participants, summary.matchId),
                    score = Component.literal(result?.finalScore?.toString() ?: "—"),
                    nameColor = if (participant.playerId == ownId) OWN_NAME_COLOR else VALUE_COLOR,
                ),
            )
        }
        if (!summary.resultsAvailable || summary.results.isEmpty()) note(Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_RESULTS_UNAVAILABLE))
        heading(MinecraftHistoryScreenKeys.SUMMARY_ROUND_COUNT)
        if (detail.rounds.isEmpty()) {
            note(Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_ROUNDS_EMPTY))
        } else {
            val rounds = detail.rounds.sortedBy { it.roundNumber }.map { round ->
                RoundRow(
                    top = 0,
                    roundNumber = round.roundNumber,
                    label = Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_ROUND_LABEL, round.roundNumber),
                    times = Component.translatable(
                        MinecraftHistoryScreenKeys.SUMMARY_ROUND_TIMES,
                        HistoryScreenText.endedAt(round.startedAtEpochMillis),
                        HistoryScreenText.endedAt(round.endedAtEpochMillis),
                        HistoryScreenText.intervalDuration(round.startedAtEpochMillis, round.endedAtEpochMillis),
                    ),
                )
            }
            roundLabelWidth = (rounds.maxOfOrNull { font.width(it.label) } ?: 0) + COLUMN_GAP
            rounds.forEach { add(it.copy(top = top)) }
        }
        return rows
    }

    /**
     * 基本資訊的名稱欄寬度：最長名稱加上欄距，最多內容寬度的一半。
     *
     * @param detail 權威摘要。
     * @return 名稱欄寬度。
     */
    private fun keyColumnWidth(detail: HistoryMatchDetailDto): Int = (
        rows(detail).filterIsInstance<FieldRow>().maxOfOrNull { font.width(it.key) }?.plus(COLUMN_GAP) ?: 0
        ).coerceAtMost((layout.right - layout.left) / 2)

    /**
     * 以名次標題及名次數值的實際字寬決定排名欄寬。
     *
     * @param detail 權威摘要。
     * @return 含欄間距的像素寬度。
     */
    private fun rankColumnWidth(detail: HistoryMatchDetailDto): Int = maxOf(
        a = font.width(Component.translatable(MinecraftHistoryScreenKeys.SUMMARY_RANK_HEADER)),
        b = detail.summary.results.maxOfOrNull { font.width(it.finalRank?.toString() ?: "—") } ?: font.width("—"),
    ) + COLUMN_GAP

    /**
     * 計算摘要總高度，與繪製的列保持一致。
     *
     * @param detail 成功摘要；null 表示尚無內容。
     * @return 總像素高度。
     */
    private fun contentHeight(detail: HistoryMatchDetailDto?): Int = detail?.let { rows(it).lastOrNull()?.let { row -> row.top + row.height } } ?: 0

    /**
     * 取得限制後的捲動偏移。
     *
     * @param detail 權威摘要。
     * @return 像素偏移。
     */
    private fun scrollOffset(detail: HistoryMatchDetailDto): Int = layout.clampScroll(scroll, contentHeight(detail)).toInt()

    /**
     * 取得列捲動後的上界。
     *
     * @param row 摘要的一列。
     * @param offset 像素捲動偏移。
     * @return 畫面上的上界。
     */
    private fun rowTop(row: SummaryRow, offset: Int): Int = layout.contentTop + row.top - offset

    /**
     * 繪製與拖曳共用幾何的捲軸。
     *
     * @param context 繪製上下文。
     * @param height 內容總高度。
     */
    private fun renderScrollbar(context: GuiGraphics, height: Int) {
        val bar = layout.scrollbar(height, scroll)
        if (bar.maximumScroll <= 0 || layout.contentBottom <= layout.contentTop) return
        VerticalScrollbar.render(context, layout.scrollbarBounds().x, bar)
    }

    /** 取得目前成功摘要，不使用失敗時殘留的舊內容計算捲軸。 */
    private fun detail(): HistoryMatchDetailDto? = session.controller.state.value.summary?.takeIf { it.status == HistoryBrowseStatus.Ready }?.detail

    /**
     * 在內容區頂端顯示載入或失敗訊息，依可用字寬換行。
     *
     * @param context 繪製上下文。
     * @param text 已翻譯的訊息。
     * @param color 文字顏色。
     */
    private fun message(context: GuiGraphics, text: Component, color: Int) {
        font.split(text, (layout.right - layout.left).coerceAtLeast(1)).forEachIndexed { index, line ->
            context.drawString(font, line, layout.left, layout.contentTop + MESSAGE_OFFSET + index * font.lineHeight, color)
        }
    }

    /** 基本資訊列上的按鈕種類。 */
    private enum class FieldAction {
        /** 複製對局 ID。 */
        COPY_MATCH_ID,

        /** 查看規則設定。 */
        RULE_SETTINGS,
    }

    /**
     * 基本資訊一列的內容。
     *
     * @property keyId 名稱翻譯鍵。
     * @property value 值。
     * @property tooltip 游標停在這一列上時的提示；沒有時值被截斷才顯示完整值。
     * @property action 列右端的按鈕；沒有時為 null。
     */
    private data class FieldContent(
        val keyId: String,
        val value: Component,
        val tooltip: List<Component>? = null,
        val action: FieldAction? = null,
    )

    /** 摘要內容的一列；上界相對內容區頂端，尚未套用捲動。 */
    private sealed interface SummaryRow {
        /** 列上界。 */
        val top: Int

        /** 列高度，含與下一列的間距。 */
        val height: Int
    }

    /**
     * 段落標題。
     *
     * @property top 列上界。
     * @property text 標題文字。
     */
    private data class HeadingRow(override val top: Int, val text: Component) : SummaryRow {
        override val height: Int get() = HEADING_HEIGHT
    }

    /**
     * 基本資訊的一列。
     *
     * @property top 列上界。
     * @property stripeIndex 在基本資訊中的順序，決定隔列底色。
     * @property key 名稱。
     * @property value 值。
     * @property tooltip 游標停在這一列上時的提示。
     * @property action 列右端的按鈕。
     */
    private data class FieldRow(
        override val top: Int,
        val stripeIndex: Int,
        val key: Component,
        val value: Component,
        val tooltip: List<Component>?,
        val action: FieldAction?,
    ) : SummaryRow {
        override val height: Int get() = FIELD_HEIGHT
    }

    /**
     * 排名表的欄位標題。
     *
     * @property top 列上界。
     */
    private data class RankHeaderRow(override val top: Int) : SummaryRow {
        override val height: Int get() = RANK_HEADER_HEIGHT
    }

    /**
     * 排名表的一列。
     *
     * @property top 列上界。
     * @property stripeIndex 在排名表中的順序，決定隔列底色。
     * @property participant 參與者資料。
     * @property rank 名次或未知佔位。
     * @property name 共用來源解析的名稱。
     * @property score 分數或未知佔位。
     * @property nameColor 名稱顏色；自己的名稱另外上色。
     */
    private data class ParticipantRow(
        override val top: Int,
        val stripeIndex: Int,
        val participant: HistoryParticipantSummaryDto,
        val rank: Component,
        val name: Component,
        val score: Component,
        val nameColor: Int,
    ) : SummaryRow {
        override val height: Int get() = PARTICIPANT_HEIGHT
    }

    /**
     * 一行說明文字。
     *
     * @property top 列上界。
     * @property text 已換行的文字。
     */
    private data class NoteRow(override val top: Int, val text: FormattedCharSequence) : SummaryRow {
        override val height: Int get() = NOTE_HEIGHT
    }

    /**
     * 一局的列，點整列開啟該局紀錄。
     *
     * @property top 列上界。
     * @property roundNumber 局序號。
     * @property label 局數文字。
     * @property times 起訖時間與時長。
     */
    private data class RoundRow(
        override val top: Int,
        val roundNumber: Int,
        val label: Component,
        val times: Component,
    ) : SummaryRow {
        override val height: Int get() = ROUND_HEIGHT
    }

    /** 列高、間距與顏色。 */
    private companion object {
        /** 段落標題的列高。 */
        const val HEADING_HEIGHT = 14

        /** 段落之間的留白。 */
        const val SECTION_GAP = 6

        /** 基本資訊的列高，容得下列上的小按鈕。 */
        const val FIELD_HEIGHT = 18

        /** 排名表欄位標題的列高。 */
        const val RANK_HEADER_HEIGHT = 12

        /** 排名表每一列的列高。 */
        const val PARTICIPANT_HEIGHT = 14

        /** 說明文字的行高。 */
        const val NOTE_HEIGHT = 11

        /** 各局列含下方間距的高度。 */
        const val ROUND_HEIGHT = 20

        /** 各局列之間的間距。 */
        const val ROUND_GAP = 2

        /** 列內文字與內容左右緣的距離。 */
        const val ROW_INSET = 6

        /** 欄與欄之間的距離。 */
        const val COLUMN_GAP = 8

        /** 頭像寬度含與名稱的間距。 */
        const val FACE_WIDTH = 14

        /** 頭像邊長。 */
        const val FACE_SIZE = 10

        /** 列上小按鈕的高度。 */
        const val MINI_BUTTON_HEIGHT = 16

        /** 列上小按鈕文字左右的留白。 */
        const val MINI_BUTTON_PADDING = 6

        /** 值與列上按鈕之間的距離。 */
        const val BUTTON_GAP = 4

        /** 垂直置中文字使用的可見字高。 */
        const val VISIBLE_TEXT_HEIGHT = 8

        /** 載入或失敗訊息與內容區頂端的距離。 */
        const val MESSAGE_OFFSET = 12

        /** 每次滾輪捲動的像素數。 */
        const val SCROLL_STEP = 18.0

        /** 游標不在內容區時交給內容區按鈕的座標。 */
        const val OFFSCREEN_POINTER = -1

        /** 結果與完整性之間的分隔。 */
        const val VALUE_SEPARATOR = " · "

        /** 複製成功提示的顯示時間。 */
        val COPIED_NOTICE = 2.seconds

        /** 段落標題顏色。 */
        const val HEADING_COLOR = 0x8ED5DF

        /** 名稱欄與表頭顏色。 */
        const val KEY_COLOR = 0xBBBBBB

        /** 值的顏色。 */
        const val VALUE_COLOR = 0xFFFFFF

        /** 自己名稱的顏色。 */
        const val OWN_NAME_COLOR = 0x8ED5DF

        /** 說明文字與次要資訊的顏色。 */
        const val NOTE_COLOR = 0xAAAAAA

        /** 載入中訊息的顏色。 */
        const val STATUS_COLOR = 0xFFFFFF

        /** 失敗訊息的顏色。 */
        const val ERROR_COLOR = 0xFF6666

        /** 各局列的底色。 */
        const val ROUND_BACKGROUND = 0x88333333.toInt()

        /** 游標停留時各局列的底色。 */
        const val ROUND_HOVER_BACKGROUND = 0xB25A5A5A.toInt()

        /** 游標停留時各局列的外框顏色。 */
        const val OUTLINE_COLOR = 0xFFFFFFFF.toInt()
    }
}
