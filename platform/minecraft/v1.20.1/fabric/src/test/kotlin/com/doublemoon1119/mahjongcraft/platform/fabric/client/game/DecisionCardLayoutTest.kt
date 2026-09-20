package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ClaimedTileMarker
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證操作介面的版面幾何。 */
class DecisionCardLayoutTest {
    /** 預覽牌不多的卡片維持緊湊固定寬度。 */
    @Test
    fun `keeps a compact card at the fixed width`() {
        val layout = layout(cardCount = 1, previewTileCount = 3)

        assertEquals(DecisionCardLayout.CARD_WIDTH, layout.cardWidth(layout.cards.single()))
    }

    /** 預覽牌變多時卡片先橫向擴張。 */
    @Test
    fun `widens a card once it holds more preview tiles`() {
        val layout = layout(cardCount = 1, previewTileCount = 8)

        assertTrue(layout.cardWidth(layout.cards.single()) > DecisionCardLayout.CARD_WIDTH)
    }

    /** 卡片寬度不超過安全畫面寬度。 */
    @Test
    fun `never widens a card past the safe screen width`() {
        val layout = layout(cardCount = 1, previewTileCount = 40, screenWidth = 400)

        val maximum = 400 - DecisionCardLayout.SCREEN_MARGIN * 2 - DecisionCardLayout.PANEL_PADDING * 2
        assertEquals(maximum, layout.cardWidth(layout.cards.single()))
    }

    /** 預覽牌在卡片寬度容納不下時才換行，並因此增加卡片高度。 */
    @Test
    fun `grows the card height only when the preview tiles wrap`() {
        val narrow = layout(cardCount = 1, previewTileCount = 40, screenWidth = 200)
        val card = narrow.cards.single()

        assertTrue(narrow.previewColumns(narrow.cardWidth(card)) < 40)
        assertTrue(narrow.cardHeight(card) > DecisionCardLayout.MIN_CARD_HEIGHT)
    }

    /** 沒有預覽牌的卡片維持最小高度。 */
    @Test
    fun `keeps a plain card at the minimum height`() {
        val layout = layout(cardCount = 1, previewTileCount = 0)

        assertEquals(DecisionCardLayout.MIN_CARD_HEIGHT, layout.cardHeight(layout.cards.single()))
    }

    /** 鳴牌指標為卡片額外保留高度。 */
    @Test
    fun `reserves height for the claimed tile marker`() {
        val plain = DecisionCard(previewTileCount = 3, hasClaimedTileMarker = false)
        val marked = DecisionCard(previewTileCount = 3, hasClaimedTileMarker = true)
        val layout = layout(cardCount = 1, previewTileCount = 3)

        assertTrue(layout.cardHeight(marked) >= layout.cardHeight(plain))
    }

    /** 卡片列高度取最高的一張。 */
    @Test
    fun `takes the row height from the tallest card`() {
        val layout = layout(cardCount = 0).copy(
            cards = listOf(
                DecisionCard(previewTileCount = 0, hasClaimedTileMarker = false),
                DecisionCard(previewTileCount = 12, hasClaimedTileMarker = true),
            ),
        )

        assertEquals(layout.cardHeight(layout.cards[1]), layout.rowHeight)
    }

    /** 沒有任何卡片時仍保留一列最小高度。 */
    @Test
    fun `keeps a minimum row height without any card`() {
        assertEquals(DecisionCardLayout.MIN_CARD_HEIGHT, layout(cardCount = 0).rowHeight)
    }

    /** 內容寬度含卡片之間的間距。 */
    @Test
    fun `adds the gaps between cards to the content width`() {
        val layout = layout(cardCount = 3, previewTileCount = 0)

        assertEquals(
            DecisionCardLayout.CARD_WIDTH * 3 + DecisionCardLayout.CARD_GAP * 2,
            layout.contentWidth,
        )
    }

    /** 內容容納得下時不需要 scrollbar，也沒有可捲動距離。 */
    @Test
    fun `needs no scrollbar while the content fits`() {
        val layout = layout(cardCount = 2, previewTileCount = 0)

        assertFalse(layout.hasOverflow)
        assertEquals(0.0, layout.maximumScroll)
    }

    /** 內容溢出時需要 scrollbar，且面板高度多留 track 的空間。 */
    @Test
    fun `reserves the scrollbar track once the content overflows`() {
        val fitting = layout(cardCount = 2, previewTileCount = 0)
        val overflowing = layout(cardCount = 12, previewTileCount = 0)

        assertTrue(overflowing.hasOverflow)
        assertEquals(
            fitting.panelHeight + DecisionCardLayout.SCROLLBAR_GAP + DecisionCardLayout.SCROLLBAR_HEIGHT,
            overflowing.panelHeight,
        )
    }

    /** 面板寬度至少容納 header。 */
    @Test
    fun `widens the panel to fit the header`() {
        val layout = layout(cardCount = 1, previewTileCount = 0, headerTextWidth = 200)

        assertEquals(
            200 + DecisionCardLayout.HEADER_SIDE_WIDTH * 2 + DecisionCardLayout.PANEL_PADDING * 2,
            layout.panelWidth,
        )
    }

    /** 面板寬度不超過安全畫面寬度。 */
    @Test
    fun `never widens the panel past the safe screen width`() {
        val layout = layout(cardCount = 12, previewTileCount = 0, screenWidth = 400)

        assertEquals(400 - DecisionCardLayout.SCREEN_MARGIN * 2, layout.panelWidth)
    }

    /** 面板水平置中；寬度為奇數時左右留白最多差一個像素。 */
    @Test
    fun `centres the panel horizontally`() {
        val layout = layout(cardCount = 2, previewTileCount = 0, screenWidth = 640)

        assertEquals(layout.panelWidth, layout.panelRight - layout.panelLeft)
        assertTrue(abs(layout.panelLeft - (640 - layout.panelRight)) <= 1)
    }

    /** 沒有觸發牌時主面板不預留上方空間。 */
    @Test
    fun `reserves no space above the panel without a trigger tile`() {
        assertEquals(0, layout(cardCount = 1).triggerAreaHeight)
    }

    /** 有觸發牌時主面板下移該區域的高度。 */
    @Test
    fun `pushes the panel below the trigger area`() {
        val layout = layout(cardCount = 1, hasTriggerTile = true, triggerLineCount = 2, triggerTextWidth = 80)

        assertTrue(layout.triggerAreaHeight > 0)
        assertEquals(layout.groupTop + layout.triggerAreaHeight, layout.panelTop)
    }

    /** 觸發文字行數增加時觸發區域跟著變高。 */
    @Test
    fun `grows the trigger area with the wrapped line count`() {
        val single = layout(cardCount = 1, hasTriggerTile = true, triggerLineCount = 1, triggerTextWidth = 80)
        val wrapped = layout(cardCount = 1, hasTriggerTile = true, triggerLineCount = 3, triggerTextWidth = 80)

        assertEquals(
            single.triggerAreaHeight + 2 * DecisionCardLayout.TEXT_LINE_HEIGHT,
            wrapped.triggerAreaHeight,
        )
    }

    /** 觸發牌面板至少和一張預覽牌一樣寬。 */
    @Test
    fun `keeps the trigger panel at least one tile wide`() {
        val layout = layout(cardCount = 1, hasTriggerTile = true, triggerLineCount = 0, triggerTextWidth = 0)

        assertEquals(
            DecisionCardLayout.PREVIEW_TILE_WIDTH + DecisionCardLayout.TRIGGER_PADDING * 2,
            layout.triggerPanelBounds.width,
        )
    }

    /** 整個群組落在畫面內。 */
    @Test
    fun `keeps the whole group inside the screen`() {
        val layout = layout(cardCount = 3, hasTriggerTile = true, triggerLineCount = 2, triggerTextWidth = 80, panelRatioY = 1.0)

        assertTrue(layout.groupTop >= 0)
        assertTrue(layout.timerTop + DecisionCardLayout.TIMER_HEIGHT <= layout.screenHeight)
    }

    /** 位置比例 0 讓群組貼齊畫面上緣。 */
    @Test
    fun `anchors the group to the top at ratio zero`() {
        assertEquals(0, layout(cardCount = 3, panelRatioY = 0.0).groupTop)
    }

    /** 內容容納得下時卡片列水平置中。 */
    @Test
    fun `centres the cards while the content fits`() {
        val layout = layout(cardCount = 2, previewTileCount = 0)
        val placements = layout.cardPlacements(scroll = 0.0)

        val leftGap = placements.first().x - layout.viewportLeft
        val rightGap = layout.viewportRight - (placements.last().x + placements.last().width)
        assertEquals(leftGap, rightGap)
        assertEquals(layout.panelLeft + DecisionCardLayout.PANEL_PADDING, layout.viewportLeft)
        assertEquals(layout.panelRight - DecisionCardLayout.PANEL_PADDING, layout.viewportRight)
    }

    /** 內容溢出時第一張卡片依捲動量左移。 */
    @Test
    fun `offsets the first card by the scroll amount`() {
        val layout = layout(cardCount = 12, previewTileCount = 0)

        assertEquals(layout.viewportLeft - 40, layout.cardPlacements(scroll = 40.0).first().x)
    }

    /** 卡片依序排列並保留間距。 */
    @Test
    fun `lays the cards out left to right with gaps`() {
        val layout = layout(cardCount = 3, previewTileCount = 0)
        val placements = layout.cardPlacements(scroll = 0.0)

        assertEquals(
            placements[0].x + placements[0].width + DecisionCardLayout.CARD_GAP,
            placements[1].x,
        )
        assertEquals(listOf(layout.cardTop, layout.cardTop, layout.cardTop), placements.map { it.y })
    }

    /** 動作按鈕貼齊卡片下緣並內縮留白。 */
    @Test
    fun `anchors the card button to the bottom of its card`() {
        val layout = layout(cardCount = 1, previewTileCount = 0)
        val placement = layout.cardPlacements(scroll = 0.0).single()

        val button = layout.cardButtonBounds(placement)
        assertEquals(placement.x + DecisionCardLayout.CARD_PADDING, button.x)
        assertEquals(placement.y + placement.height - DecisionCardLayout.BUTTON_HEIGHT - DecisionCardLayout.CARD_PADDING, button.y)
        assertEquals(placement.width - DecisionCardLayout.CARD_PADDING * 2, button.width)
    }

    /** 預覽牌排在動作按鈕上方並水平置中。 */
    @Test
    fun `centres a single row of preview tiles above the button`() {
        val layout = layout(cardCount = 1, previewTileCount = 3)
        val placement = layout.cardPlacements(scroll = 0.0).single()

        val tiles = layout.previewTilePlacements(placement, 3)
        assertEquals(3, tiles.size)
        assertEquals(listOf(tiles[0].y), tiles.map { it.y }.distinct())
        val leftGap = tiles.first().x - placement.x
        val rightGap = placement.x + placement.width - (tiles.last().x + tiles.last().width)
        assertEquals(leftGap, rightGap)
        assertEquals(
            layout.cardButtonBounds(placement).y - DecisionCardLayout.TILE_BUTTON_GAP,
            tiles.first().y + tiles.first().height,
        )
    }

    /** 預覽牌超過一列時換行，最後一列自己置中。 */
    @Test
    fun `wraps the preview tiles into rows`() {
        val layout = layout(cardCount = 1, previewTileCount = 3)
        val placement = layout.cardPlacements(scroll = 0.0).single()
        val columns = layout.previewColumns(placement.width)

        val tiles = layout.previewTilePlacements(placement, columns + 1)

        assertEquals(2, tiles.map { it.y }.distinct().size)
        assertEquals(tiles.first().x, tiles[columns - 1].x - (columns - 1) * (DecisionCardLayout.PREVIEW_TILE_WIDTH + DecisionCardLayout.PREVIEW_TILE_GAP))
    }

    /** 沒有預覽牌時沒有任何牌面座標。 */
    @Test
    fun `places no preview tile for an empty card`() {
        val layout = layout(cardCount = 1, previewTileCount = 0)

        assertEquals(emptyList(), layout.previewTilePlacements(layout.cardPlacements(scroll = 0.0).single(), 0))
    }

    /** 鳴牌指標落在預覽牌上方。 */
    @Test
    fun `puts the claimed tile marker above its tile`() {
        val layout = layout(cardCount = 1, previewTileCount = 3)
        val tile = layout.previewTilePlacements(layout.cardPlacements(scroll = 0.0).single(), 3).first()

        assertEquals(
            tile.y - ClaimedTileMarker.GAP - ClaimedTileMarker.height,
            layout.claimedTileMarkerTop(tile),
        )
    }

    /** 沒有溢出時 thumb 佔滿整條 track。 */
    @Test
    fun `fills the track with the thumb while the content fits`() {
        val layout = layout(cardCount = 2, previewTileCount = 0)

        assertEquals(layout.viewportWidth, layout.scrollbarThumb(scroll = 0.0).width)
    }

    /** 溢出越多 thumb 越短，且不短於最小寬度。 */
    @Test
    fun `shrinks the thumb as the content overflows`() {
        val layout = layout(cardCount = 40, previewTileCount = 0)

        val thumb = layout.scrollbarThumb(scroll = 0.0)
        assertTrue(thumb.width < layout.viewportWidth)
        assertTrue(thumb.width >= DecisionCardLayout.MIN_SCROLLBAR_THUMB_WIDTH)
    }

    /** Viewport 比最小 thumb 寬度還窄時，最小寬度讓給可見寬度。 */
    @Test
    fun `clamps the thumb to a viewport narrower than the minimum width`() {
        val layout = layout(cardCount = 40, previewTileCount = 0, screenWidth = 40)

        assertTrue(layout.scrollbarThumb(scroll = 0.0).width <= layout.viewportWidth)
    }

    /** 捲到底時 thumb 貼齊 track 右端。 */
    @Test
    fun `moves the thumb to the end at the maximum scroll`() {
        val layout = layout(cardCount = 12, previewTileCount = 0)

        assertEquals(layout.viewportRight, layout.scrollbarThumb(layout.maximumScroll).right)
    }

    /** Thumb 左界換算回捲動量，並夾在合法範圍內。 */
    @Test
    fun `converts a thumb position back into a scroll amount`() {
        val layout = layout(cardCount = 12, previewTileCount = 0)

        assertEquals(0.0, layout.scrollFromThumbLeft(thumbLeft = -500.0, scroll = 0.0))
        assertEquals(layout.maximumScroll, layout.scrollFromThumbLeft(thumbLeft = 5000.0, scroll = 0.0))
    }

    /** 跳過按鈕貼齊面板右上角。 */
    @Test
    fun `anchors the skip button to the top right of the panel`() {
        val layout = layout(cardCount = 2, previewTileCount = 0)

        val skip = layout.skipButtonBounds
        assertEquals(layout.panelRight - DecisionCardLayout.PANEL_PADDING, skip.x + skip.width)
        assertEquals(layout.panelTop + DecisionCardLayout.PANEL_PADDING, skip.y)
    }

    /** 倒數接在面板下方。 */
    @Test
    fun `puts the timer below the panel`() {
        val layout = layout(cardCount = 2, previewTileCount = 0)

        assertEquals(layout.panelBottom + DecisionCardLayout.TIMER_PANEL_GAP, layout.timerTop)
    }

    /** 觸發文字的換行寬度至少為 1，即使畫面極窄。 */
    @Test
    fun `keeps the trigger wrap width positive on a tiny screen`() {
        assertEquals(1, DecisionCardLayout.triggerTextMaximumWidth(screenWidth = 1))
    }

    /** 建立測試用的版面。 */
    private fun layout(
        cardCount: Int,
        previewTileCount: Int = 0,
        screenWidth: Int = 854,
        screenHeight: Int = 480,
        headerTextWidth: Int = 60,
        triggerLineCount: Int = 0,
        triggerTextWidth: Int = 0,
        hasTriggerTile: Boolean = false,
        panelRatioY: Double = 0.5,
    ) = DecisionCardLayout(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        cards = List(cardCount) { DecisionCard(previewTileCount, hasClaimedTileMarker = false) },
        headerTextWidth = headerTextWidth,
        triggerLineCount = triggerLineCount,
        triggerTextWidth = triggerTextWidth,
        hasTriggerTile = hasTriggerTile,
        panelRatioY = panelRatioY,
    )
}
