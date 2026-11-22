package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayFactDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryBrowseReplayState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryCardStyle
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryFactPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryRoundEventPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history.HistoryWinningHandSource
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.max

/**
 * 回放的結算卡片，疊在場景中間偏下，內容與事件頁的結算相同：顯示這一局到目前這一步為止最近的一筆結算，例如胡牌後的
 * 後續處理那一步仍顯示剛才的胡牌結算。
 *
 * 預設收起，由玩家展開；展開後換步仍保持展開，換局時回到收起。內容放不下時可用滾輪或拖動捲軸捲動。
 *
 * @property session 規則與事件的來源。
 * @property textRenderer 文字繪製器。
 * @property outcomeRows 結算內容的各列。
 * @property rowRenderer 各列的繪製。
 * @property eventPresenter 交易的呈現。
 * @property actorText 依初始座位取得玩家名稱。
 * @property participant 依初始座位取得頭像來源。
 */
internal class HistoryReplaySettlementCard(
    private val session: HistoryBrowseSession,
    private val textRenderer: Font,
    private val outcomeRows: HistoryOutcomeCardRows,
    private val rowRenderer: HistoryCardRowRenderer,
    private val eventPresenter: HistoryRoundEventPresenter,
    private val actorText: (Int) -> Component,
    private val participant: (Int) -> HistoryParticipantSummaryDto?,
) {
    /** 卡片是否收起。 */
    var collapsed = true

    /** 目前顯示的局序號與結算所在的交易索引；換局時收起，換一筆結算時捲回頂端。 */
    private var shownSettlement: Pair<Int, Int>? = null

    /** 上一次畫出的捲軸位置與幾何；沒有捲軸時為 null。 */
    private var scrollbar: Pair<Int, ScrollbarLayout>? = null

    /** 是否正在拖動捲軸。 */
    private var scrollDragging = false

    /** 拖動時游標在捲軸滑塊內的抓取偏移。 */
    private var scrollGrabOffset = 0.0

    /** 內容的捲動偏移。 */
    private var scroll = 0.0

    /** 已排好的內容，依局序號、步數與寬度。 */
    private var cache: Pair<Triple<Int, Int, Int>, List<HistoryCardRow>>? = null

    /** 上一次畫出的範圍；沒有畫出時為 null。 */
    var bounds: HistoryScreenLayout.Bounds? = null
        private set

    /**
     * 到目前這一步為止是否有結算可以顯示。
     *
     * @param replay 目前回放。
     * @return 有結算時為 true。
     */
    fun available(replay: HistoryBrowseReplayState?): Boolean = replay != null && latestSettlementIndex(replay) != null

    /**
     * 畫出卡片；還沒有結算或已收起時不畫。
     *
     * @param context 繪製上下文。
     * @param scene 場景範圍。
     * @param bottom 卡片下緣不超過的位置，例如全螢幕控制列的上緣。
     * @param replay 目前回放。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標指著的內容提示；沒有時為 null。
     */
    fun render(context: GuiGraphics, scene: HistoryScreenLayout.Bounds, bottom: Int, replay: HistoryBrowseReplayState?, mouseX: Int, mouseY: Int): List<Component>? {
        bounds = null
        replay ?: return null
        val index = latestSettlementIndex(replay)
        if (replay.roundNumber != shownSettlement?.first) collapsed = true
        if (replay.roundNumber to (index ?: -1) != shownSettlement) {
            shownSettlement = replay.roundNumber to (index ?: -1)
            scroll = 0.0
        }
        scrollbar = null
        if (collapsed || index == null) return null
        val width = minOf(scene.width - MARGIN * 2, MAX_WIDTH)
        if (width <= 0) return null
        val rows = rows(replay, index, width - PADDING * 2)
        if (rows.isEmpty()) return null
        val contentHeight = rows.sumOf { it.height }
        val maxHeight = ((bottom - scene.y) * MAX_HEIGHT_RATIO).toInt()
        val height = minOf(contentHeight + TITLE_HEIGHT + PADDING * 2, maxHeight).coerceAtLeast(TITLE_HEIGHT + PADDING * 2)
        val card = HistoryScreenLayout.Bounds(x = scene.x + (scene.width - width) / 2, y = bottom - MARGIN - height, width = width, height = height)
        bounds = card
        context.fill(card.x, card.y, card.right, card.bottom, BACKGROUND)
        context.drawString(textRenderer, Component.translatable(MinecraftHistoryScreenKeys.REPLAY_SETTLEMENT), card.x + PADDING, card.y + PADDING, HistoryCardStyle.ACCENT_COLOR)
        val contentTop = card.y + PADDING + TITLE_HEIGHT
        val viewHeight = card.bottom - PADDING - contentTop
        scroll = scroll.coerceIn(0.0, max(0, contentHeight - viewHeight).toDouble())
        val hovered = mouseX in card.x until card.right && mouseY in contentTop until card.bottom - PADDING
        var tooltip: List<Component>? = null
        context.enableScissor(card.x, contentTop, card.right, card.bottom - PADDING)
        try {
            var y = contentTop - scroll.toInt()
            rows.forEach { row ->
                if (y + row.height >= contentTop && y < card.bottom) {
                    rowRenderer.render(context, row, card.x + PADDING, y, hovered, mouseX, mouseY)?.let { tooltip = it }
                }
                y += row.height
            }
        } finally {
            context.disableScissor()
        }
        if (contentHeight > viewHeight) {
            val layout = ScrollbarLayout(
                trackTop = contentTop,
                trackBottom = contentTop + viewHeight,
                itemCount = contentHeight,
                visibleItemCount = viewHeight,
                scrollIndex = scroll.toInt(),
                minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT,
            )
            val left = card.right - VerticalScrollbar.WIDTH - 2
            scrollbar = left to layout
            VerticalScrollbar.render(context = context, left = left, layout = layout)
        }
        return tooltip
    }

    /**
     * 游標是否在卡片上。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 在卡片上時為 true。
     */
    fun contains(mouseX: Double, mouseY: Double): Boolean = bounds?.let { mouseX >= it.x && mouseX < it.right && mouseY >= it.y && mouseY < it.bottom } == true

    /**
     * 在卡片上捲動內容。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param amount 滾輪量。
     * @return 游標在卡片上時為 true。
     */
    fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (!contains(mouseX, mouseY)) return false
        scroll -= amount * HistoryCardStyle.LINE_HEIGHT * SCROLL_LINES
        return true
    }

    /**
     * 按下捲軸時開始拖動，按在軌道空白處時跳到那裡。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 按在捲軸上時為 true。
     */
    fun mouseClicked(mouseX: Double, mouseY: Double): Boolean {
        val (left, layout) = scrollbar ?: return false
        if (mouseX < left - SCROLLBAR_HIT_PADDING || mouseX >= left + VerticalScrollbar.WIDTH + SCROLLBAR_HIT_PADDING) return false
        if (mouseY < layout.trackTop || mouseY >= layout.trackBottom) return false
        scrollDragging = true
        scrollGrabOffset = layout.grabOffset(mouseY)
        scroll = layout.scrollIndexFor(mouseY, scrollGrabOffset).toDouble()
        return true
    }

    /**
     * 拖動捲軸時跟著游標捲動。
     *
     * @param mouseY 游標垂直座標。
     * @return 正在拖動時為 true。
     */
    fun mouseDragged(mouseY: Double): Boolean {
        if (!scrollDragging) return false
        scrollbar?.let { (_, layout) -> scroll = layout.scrollIndexFor(mouseY, scrollGrabOffset).toDouble() }
        return true
    }

    /**
     * 放開滑鼠時結束拖動。
     *
     * @return 原本正在拖動時為 true。
     */
    fun mouseReleased(): Boolean {
        val dragging = scrollDragging
        scrollDragging = false
        return dragging
    }

    /**
     * 到目前這一步為止最近一筆帶結算的交易。
     *
     * @param replay 目前回放。
     * @return 交易索引；還沒有結算時為 null。
     */
    private fun latestSettlementIndex(replay: HistoryBrowseReplayState): Int? = replay.eventPages
        .flatMap { it.transactions }
        .filter { transaction ->
            transaction.index < replay.step &&
                transaction.facts.any { fact ->
                    (fact as? HistoryReplayFactDto.Completion)?.outcome != null || (fact as? HistoryReplayFactDto.RuleEffect)?.outcome != null
                }
        }
        .maxOfOrNull { it.index }

    /**
     * 一筆交易中帶結算的事實。
     *
     * @param replay 目前回放。
     * @param index 交易索引。
     * @return 呈現內容與保存原文；沒有時為空。
     */
    private fun outcomeFacts(replay: HistoryBrowseReplayState, index: Int): List<HistoryFactPresentation> {
        val page = replay.eventPages.firstOrNull { page -> page.transactions.any { it.index == index } } ?: return emptyList()
        val ruleId = session.controller.state.value.summary?.detail?.summary?.ruleId
        return eventPresenter.present(page, ruleId).transactions.firstOrNull { it.index == index }?.facts?.filter { it.outcome != null }.orEmpty()
    }

    /**
     * 排好一筆結算的各列。
     *
     * @param replay 目前回放。
     * @param index 結算所在的交易索引。
     * @param contentWidth 內容寬度。
     * @return 各列；沒有結算時為空。
     */
    private fun rows(replay: HistoryBrowseReplayState, index: Int, contentWidth: Int): List<HistoryCardRow> {
        val key = Triple(replay.roundNumber, index, contentWidth)
        cache?.takeIf { it.first == key }?.let { return it.second }
        val facts = outcomeFacts(replay, index)
        if (facts.isEmpty()) return emptyList()
        val ruleId = session.controller.state.value.summary?.detail?.summary?.ruleId
        val builder = HistoryCardRowsBuilder(textRenderer, contentWidth)
        val state = replay.state
        facts.forEach { fact ->
            val saved = when (val source = fact.source) {
                is HistoryReplayFactDto.Completion -> source.outcome
                is HistoryReplayFactDto.RuleEffect -> source.outcome
                else -> null
            }
            outcomeRows.add(
                builder = builder,
                outcome = checkNotNull(fact.outcome),
                savedOutcome = saved,
                ruleId = ruleId,
                transactionIndex = index,
                actorText = actorText,
                participant = participant,
                winningHand = { state?.let { HistoryWinningHandSource.Ready(it) } ?: HistoryWinningHandSource.Loading },
            )
        }
        val rows = builder.rows.filterNot { it is HistoryCardRow.StateLink }
        if (state != null) cache = key to rows
        return rows
    }

    /** 卡片的尺寸與配色。 */
    private companion object {
        /** 卡片與場景邊緣的距離。 */
        const val MARGIN = 8

        /** 卡片內距。 */
        const val PADDING = 6

        /** 卡片標題列的高度。 */
        const val TITLE_HEIGHT = 14

        /** 卡片最大寬度。 */
        const val MAX_WIDTH = 340

        /** 卡片最多佔場景高度的比例。 */
        const val MAX_HEIGHT_RATIO = 0.6

        /** 滾輪一格捲動的列數。 */
        const val SCROLL_LINES = 3

        /** 捲軸左右可點擊的額外範圍。 */
        const val SCROLLBAR_HIT_PADDING = 2

        /** 卡片背景。 */
        const val BACKGROUND = 0xC0202020.toInt()
    }
}
