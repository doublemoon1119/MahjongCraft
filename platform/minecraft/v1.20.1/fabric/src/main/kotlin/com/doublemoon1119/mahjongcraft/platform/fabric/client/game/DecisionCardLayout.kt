package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.hudCoordinate
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.HorizontalScrollLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.HorizontalScrollThumb

/** 一個矩形版位。 */
internal data class DecisionBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * 單一操作卡片會影響版面的特徵。
 *
 * @property previewTileCount 卡片內的預覽牌張數。
 * @property hasClaimedTileMarker 是否需要在某張預覽牌上方保留鳴牌指標的高度。
 */
internal data class DecisionCard(
    val previewTileCount: Int,
    val hasClaimedTileMarker: Boolean,
)

/**
 * 操作介面的版面幾何。
 *
 * 卡片排成固定單列並在寬度不足時水平捲動；面板、觸發牌、倒數三者構成一個整體群組，群組的垂直位置由玩家
 * 在 HUD 編輯器調整的比例決定。
 *
 * 這個類別不依賴 `Screen` 或 `DrawContext`：畫面尺寸、玩家設定的位置比例，以及兩處需要實際量測的文字寬度
 * （面板標題與觸發文字換行結果）全部由呼叫端傳入。捲動量是每幀變動的輸入，因此由需要它的方法接收，不進
 * 建構子。
 *
 * @property screenWidth 目前 GUI scaled 畫面寬度。
 * @property screenHeight 目前 GUI scaled 畫面高度。
 * @property cards 由左至右的卡片特徵；空清單時仍保留一列最小高度。
 * @property headerTextWidth 面板標題的實際像素寬度。
 * @property triggerLineCount 觸發文字換行後的行數；沒有觸發文字時為 0。
 * @property triggerTextWidth 觸發文字最寬一行的像素寬度；沒有觸發文字時為 0。
 * @property hasTriggerTile 是否有觸發牌需要在主面板上方保留空間。
 * @property panelRatioY 玩家設定的操作群組垂直位置比例。
 */
internal data class DecisionCardLayout(
    val screenWidth: Int,
    val screenHeight: Int,
    val cards: List<DecisionCard>,
    val headerTextWidth: Int,
    val triggerLineCount: Int,
    val triggerTextWidth: Int,
    val hasTriggerTile: Boolean,
    val panelRatioY: Double,
) {
    /** 單一卡片的寬度；預覽牌不多時維持緊湊，牌多時優先橫向擴張到畫面允許的上限。 */
    fun cardWidth(card: DecisionCard): Int {
        if (card.previewTileCount <= COMPACT_PREVIEW_TILE_COUNT) return CARD_WIDTH
        val desired = CARD_PADDING * 2 +
            card.previewTileCount * PREVIEW_TILE_WIDTH +
            (card.previewTileCount - 1).coerceAtLeast(0) * PREVIEW_TILE_GAP
        return desired.coerceIn(CARD_WIDTH, (screenWidth - SCREEN_MARGIN * 2 - PANEL_PADDING * 2).coerceAtLeast(CARD_WIDTH))
    }

    /** 單一卡片的高度；預覽牌只有在卡片寬度不足時才換行。 */
    fun cardHeight(card: DecisionCard): Int {
        val columns = previewColumns(cardWidth(card))
        val tileRows = ((card.previewTileCount + columns - 1) / columns).coerceAtLeast(1)
        val markerHeight = if (card.hasClaimedTileMarker) CLAIMED_TILE_MARKER_ROW_WIDTHS.size + CLAIMED_TILE_MARKER_GAP else 0
        return maxOf(
            MIN_CARD_HEIGHT,
            CARD_PADDING * 2 + markerHeight + tileRows * PREVIEW_TILE_HEIGHT + (tileRows - 1) * PREVIEW_TILE_GAP + BUTTON_HEIGHT,
        )
    }

    /** 指定寬度的卡片一列可容納的預覽牌數。 */
    fun previewColumns(cardWidth: Int): Int = ((cardWidth - CARD_PADDING * 2 + PREVIEW_TILE_GAP) / (PREVIEW_TILE_WIDTH + PREVIEW_TILE_GAP)).coerceAtLeast(1)

    /** 卡片列的高度，取最高的一張。 */
    val rowHeight: Int
        get() = cards.maxOfOrNull(::cardHeight) ?: MIN_CARD_HEIGHT

    /** 全部卡片與間距所需的內容寬度。 */
    val contentWidth: Int
        get() = cards.sumOf(::cardWidth) + (cards.size - 1).coerceAtLeast(0) * CARD_GAP

    /** 內容是否超出可見寬度，決定是否需要 scrollbar。 */
    val hasOverflow: Boolean
        get() = contentWidth > viewportWidth

    /** 操作卡列的水平捲動幾何。 */
    private val scroll: HorizontalScrollLayout
        get() = HorizontalScrollLayout(
            viewportLeft = viewportLeft,
            viewportWidth = viewportWidth,
            contentWidth = contentWidth,
            minimumThumbWidth = MIN_SCROLLBAR_THUMB_WIDTH,
        )

    /** 可捲動的最大距離。 */
    val maximumScroll: Double
        get() = scroll.maximumScroll

    /** 面板寬度；內容可容納時收合，溢出時使用整個安全畫面寬度。 */
    val panelWidth: Int
        get() {
            val cardsWidth = contentWidth + PANEL_PADDING * 2
            val headerWidth = headerTextWidth + HEADER_SIDE_WIDTH * 2 + PANEL_PADDING * 2
            return maxOf(cardsWidth, headerWidth).coerceAtMost((screenWidth - SCREEN_MARGIN * 2).coerceAtLeast(1))
        }

    /** 面板左界。 */
    val panelLeft: Int
        get() = (screenWidth - panelWidth) / 2

    /** 面板右界。 */
    val panelRight: Int
        get() = (screenWidth + panelWidth) / 2

    /** 面板高度；選項數量只增加內容寬度，不增加高度。 */
    val panelHeight: Int
        get() = PANEL_PADDING * 2 + HEADER_HEIGHT + rowHeight + if (hasOverflow) SCROLLBAR_GAP + SCROLLBAR_HEIGHT else 0

    /** 觸發牌面板存在時，主面板上方保留的完整高度與間距。 */
    val triggerAreaHeight: Int
        get() = if (hasTriggerTile) TRIGGER_PADDING * 2 + triggerTextHeight + PREVIEW_TILE_HEIGHT + PANEL_GAP else 0

    /** 面板、觸發牌與倒數形成的完整群組高度。 */
    val groupHeight: Int
        get() = triggerAreaHeight + panelHeight + TIMER_PANEL_GAP + TIMER_HEIGHT

    /** 完整群組的上界。 */
    val groupTop: Int
        get() = hudCoordinate(panelRatioY, screenHeight, groupHeight)

    /** 主面板上界。 */
    val panelTop: Int
        get() = groupTop + triggerAreaHeight

    /** 主面板下界。 */
    val panelBottom: Int
        get() = panelTop + panelHeight

    /** 標題文字的上緣；在 header 的按鈕高度內垂直置中。 */
    fun headerTextTop(fontHeight: Int): Int = panelTop + PANEL_PADDING + (BUTTON_HEIGHT - fontHeight) / 2

    /** 卡片列上界。 */
    val cardTop: Int
        get() = panelTop + PANEL_PADDING + HEADER_HEIGHT

    /** 卡片列下界。 */
    val cardBottom: Int
        get() = cardTop + rowHeight

    /** 可捲動 viewport 的左界。 */
    val viewportLeft: Int
        get() = panelLeft + PANEL_PADDING

    /** 可捲動 viewport 的右界。 */
    val viewportRight: Int
        get() = panelRight - PANEL_PADDING

    /** 可捲動 viewport 的可見寬度。 */
    val viewportWidth: Int
        get() = viewportRight - viewportLeft

    /** Scrollbar track 的上界。 */
    val scrollbarTop: Int
        get() = cardBottom + SCROLLBAR_GAP

    /** 倒數的上界。 */
    val timerTop: Int
        get() = panelBottom + TIMER_PANEL_GAP

    /** 跳過按鈕的版位。 */
    val skipButtonBounds: DecisionBounds
        get() = DecisionBounds(
            x = panelRight - SKIP_BUTTON_WIDTH - PANEL_PADDING,
            y = panelTop + PANEL_PADDING,
            width = SKIP_BUTTON_WIDTH,
            height = BUTTON_HEIGHT,
        )

    /** 全部卡片排成單列後的版位；內容未溢出時置中，溢出時套用捲動 offset。 */
    fun cardPlacements(scroll: Double): List<DecisionBounds> {
        var x = if (hasOverflow) viewportLeft - scroll.toInt() else viewportLeft + (viewportWidth - contentWidth) / 2
        return cards.map { card ->
            val width = cardWidth(card)
            DecisionBounds(x, cardTop, width, rowHeight).also { x += width + CARD_GAP }
        }
    }

    /** 卡片內動作按鈕的版位。 */
    fun cardButtonBounds(placement: DecisionBounds): DecisionBounds = DecisionBounds(
        x = placement.x + CARD_PADDING,
        y = placement.y + placement.height - BUTTON_HEIGHT - CARD_PADDING,
        width = placement.width - CARD_PADDING * 2,
        height = BUTTON_HEIGHT,
    )

    /** 卡片內預覽牌由左至右、由上而下的座標；每一列各自水平置中。 */
    fun previewTilePlacements(placement: DecisionBounds, tileCount: Int): List<DecisionBounds> {
        if (tileCount <= 0) return emptyList()
        val columns = previewColumns(placement.width)
        val rows = (tileCount + columns - 1) / columns
        val previewHeight = rows * PREVIEW_TILE_HEIGHT + (rows - 1) * PREVIEW_TILE_GAP
        val previewTop = cardButtonBounds(placement).y - TILE_BUTTON_GAP - previewHeight
        return List(tileCount) { index ->
            val row = index / columns
            val rowStart = row * columns
            val rowTileCount = minOf(rowStart + columns, tileCount) - rowStart
            val rowWidth = rowTileCount * PREVIEW_TILE_WIDTH + (rowTileCount - 1) * PREVIEW_TILE_GAP
            DecisionBounds(
                x = placement.x + (placement.width - rowWidth) / 2 + (index - rowStart) * (PREVIEW_TILE_WIDTH + PREVIEW_TILE_GAP),
                y = previewTop + row * (PREVIEW_TILE_HEIGHT + PREVIEW_TILE_GAP),
                width = PREVIEW_TILE_WIDTH,
                height = PREVIEW_TILE_HEIGHT,
            )
        }
    }

    /** 鳴牌指標的上界，位於指定預覽牌的正上方。 */
    fun claimedTileMarkerTop(tile: DecisionBounds): Int = tile.y - CLAIMED_TILE_MARKER_GAP - CLAIMED_TILE_MARKER_ROW_WIDTHS.size

    /** 依可見比例縮放的 scrollbar thumb。 */
    fun scrollbarThumb(scroll: Double): HorizontalScrollThumb = this.scroll.thumb(scroll)

    /** 將 thumb 左界換算回內容捲動量。 */
    fun scrollFromThumbLeft(thumbLeft: Double, scroll: Double): Double = this.scroll.scrollFromThumbLeft(thumbLeft, scroll)

    /** 依 thumb 拖曳位移計算新的捲動量。 */
    fun scrollFromDrag(startScroll: Double, pointerDelta: Double): Double = scroll.scrollFromDrag(startScroll, pointerDelta)

    /** 依滾輪量計算新的捲動量。 */
    fun scrollFromWheel(currentScroll: Double, amount: Double): Double = scroll.scrollFromWheel(currentScroll, amount, SCROLL_STEP)

    /** 觸發牌面板的版位；高度只由觸發文字行數決定。 */
    val triggerPanelBounds: DecisionBounds
        get() {
            val width = maxOf(PREVIEW_TILE_WIDTH, triggerTextWidth) + TRIGGER_PADDING * 2
            val height = TRIGGER_PADDING * 2 + triggerTextHeight + PREVIEW_TILE_HEIGHT
            return DecisionBounds((screenWidth - width) / 2, panelTop - height - PANEL_GAP, width, height)
        }

    /** 觸發文字第 [index] 行的上界。 */
    fun triggerTextLineTop(index: Int): Int = triggerPanelBounds.y + TRIGGER_PADDING + index * TEXT_LINE_HEIGHT

    /** 觸發牌的版位。 */
    val triggerTileBounds: DecisionBounds
        get() = DecisionBounds(
            x = screenWidth / 2 - PREVIEW_TILE_WIDTH / 2,
            y = triggerPanelBounds.y + TRIGGER_PADDING + triggerTextHeight,
            width = PREVIEW_TILE_WIDTH,
            height = PREVIEW_TILE_HEIGHT,
        )

    /** 觸發文字連同其與觸發牌之間的間距所佔高度。 */
    private val triggerTextHeight: Int
        get() = if (triggerLineCount == 0) 0 else triggerLineCount * TEXT_LINE_HEIGHT + TRIGGER_GAP

    internal companion object {
        /** 觸發文字換行前可用的最大寬度。 */
        fun triggerTextMaximumWidth(screenWidth: Int): Int = (screenWidth - SCREEN_MARGIN * 2 - TRIGGER_PADDING * 2).coerceAtLeast(1)

        const val CARD_WIDTH = 92
        const val COMPACT_PREVIEW_TILE_COUNT = 4
        const val MIN_CARD_HEIGHT = 62
        const val CARD_PADDING = 4
        const val CARD_GAP = 5
        const val BUTTON_HEIGHT = 20
        const val SKIP_BUTTON_WIDTH = 48
        const val PANEL_PADDING = 6
        const val HEADER_HEIGHT = 24
        const val HEADER_SIDE_WIDTH = 58
        const val PREVIEW_TILE_WIDTH = 18
        const val PREVIEW_TILE_HEIGHT = 24
        const val PREVIEW_TILE_GAP = 2
        const val TILE_BUTTON_GAP = 3
        const val TRIGGER_PADDING = 6
        const val TRIGGER_GAP = 3
        const val PANEL_GAP = 5
        const val SCREEN_MARGIN = 8
        const val TEXT_LINE_HEIGHT = 10
        const val TIMER_PANEL_GAP = 5
        const val TIMER_HEIGHT = 14
        const val SCROLLBAR_GAP = 4
        const val SCROLLBAR_HEIGHT = 4
        const val MIN_SCROLLBAR_THUMB_WIDTH = 18

        /** 滾輪一格捲動的距離。 */
        const val SCROLL_STEP = 48.0

        /** 鳴牌指標與預覽牌之間的間距（像素）。 */
        const val CLAIMED_TILE_MARKER_GAP = 3

        /**
         * 吃卡片標出鳴來那張牌的倒三角形指標，由上而下每列的寬度（像素，皆為奇數以確保左右對稱）；
         * 碰／槓不使用，牌面彼此完全相同，標記沒有辨識意義。
         */
        val CLAIMED_TILE_MARKER_ROW_WIDTHS = intArrayOf(9, 7, 5, 3, 1)
    }
}
