package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.SettlementRankingColumnId.DELTA
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.SettlementRankingColumnId.FACE
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.SettlementRankingColumnId.NAME
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.SettlementRankingColumnId.RANK
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.SettlementRankingColumnId.SCORE
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.SettlementRankingColumnId.STATUS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證結算排名面板的欄位配置與欄寬。 */
class SettlementRankingLayoutTest {
    /** 標準欄位依序排開，同組與跨組各用自己的間距。 */
    @Test
    fun `lays the standard columns out from left to right`() {
        val layout = layout(statusWidth = null, panelPadding = 12f)

        assertEquals(139f, layout.panelHalfWidth)
        assertEquals(SettlementRankingColumnSpan(left = -127f, width = 12f), layout[RANK])
        assertEquals(SettlementRankingColumnSpan(left = -108f, width = 10f), layout[FACE])
        assertEquals(SettlementRankingColumnSpan(left = -91f, width = 96f), layout[NAME])
        assertEquals(SettlementRankingColumnSpan(left = 14f, width = 48f), layout[SCORE])
        assertEquals(SettlementRankingColumnSpan(left = 71f, width = 56f), layout[DELTA])
    }

    /** 狀態欄插在名稱與分數之間，後面的欄位跟著右移。 */
    @Test
    fun `inserts the status column between the name and the score`() {
        val layout = layout(statusWidth = 50f, panelPadding = 8f)

        assertEquals(164.5f, layout.panelHalfWidth)
        assertEquals(-120.5f, layout[NAME].left)
        assertEquals(SettlementRankingColumnSpan(left = -15.5f, width = 50f), layout[STATUS])
        assertEquals(9.5f, layout[STATUS].centerX)
        assertEquals(43.5f, layout[SCORE].left)
        assertEquals(156.5f, layout[DELTA].right)
    }

    /** 第一欄與最後一欄到面板邊緣都剛好是留白寬度，面板左右對稱。 */
    @Test
    fun `leaves the panel padding on both sides`() {
        val layout = layout(statusWidth = 37f, panelPadding = 5f)

        assertEquals(-layout.panelHalfWidth + 5f, layout[RANK].left)
        assertEquals(layout.panelHalfWidth - 5f, layout[DELTA].right)
    }

    /** 不含狀態欄時查詢狀態欄會失敗，而不是回傳一個假的位置。 */
    @Test
    fun `has no status column unless one is given`() {
        assertFailsWith<NoSuchElementException> { layout(statusWidth = null, panelPadding = 12f)[STATUS] }
    }

    /** 只有一欄時不套用它的前置間距。 */
    @Test
    fun `ignores the leading gap of the first column`() {
        val layout = SettlementRankingLayout.arrange(
            columns = listOf(SettlementRankingColumn(SCORE, width = 20f, gapBefore = 30f)),
            panelPadding = 4f,
        )

        assertEquals(14f, layout.panelHalfWidth)
        assertEquals(SettlementRankingColumnSpan(left = -10f, width = 20f), layout[SCORE])
    }

    /** 欄寬隨最寬的內容成長，並加上兩側留白。 */
    @Test
    fun `grows a column with its widest content`() {
        assertEquals(72f, SettlementRankingLayout.contentColumnWidth(listOf(30f, 60f, 45f), padding = 6f, minWidth = 48f))
    }

    /** 內容較窄或沒有內容時維持最小寬度。 */
    @Test
    fun `keeps the minimum width for narrow or no content`() {
        assertEquals(48f, SettlementRankingLayout.contentColumnWidth(listOf(20f), padding = 6f, minWidth = 48f))
        assertEquals(48f, SettlementRankingLayout.contentColumnWidth(emptyList(), padding = 6f, minWidth = 48f))
    }

    /** 分數增減：正數帶加號，負數保留負號，零顯示正負零。 */
    @Test
    fun `formats a score delta with its sign`() {
        assertEquals("+8000", SettlementRankingLayout.formatDelta(8000))
        assertEquals("-3900", SettlementRankingLayout.formatDelta(-3900))
        assertEquals("±0", SettlementRankingLayout.formatDelta(0))
    }

    /** 以兩個面板共用的欄位建立版面：頭像 10、分數 48、增減 56。 */
    private fun layout(
        statusWidth: Float?,
        panelPadding: Float,
    ) = SettlementRankingLayout.arrange(
        columns = SettlementRankingLayout.standardColumns(
            faceSize = 10f,
            scoreWidth = 48f,
            deltaWidth = 56f,
            statusWidth = statusWidth,
        ),
        panelPadding = panelPadding,
    )
}
