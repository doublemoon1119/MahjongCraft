package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DiscardReadinessAnalysisDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WIN_AVAILABLE_ID
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.WaitingTileAvailabilityDto
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證捨牌分析面板的內容組成與版面幾何。 */
class DiscardAnalysisPresentationTest {
    /** 以內建登記解析狀態文字的測試解析器。 */
    private val texts = testDecisionTextResolver()

    /** 每張等待牌各一格，並帶自己的剩餘張數。 */
    @Test
    fun `builds one cell per waiting tile`() {
        val content = discardAnalysisContent(texts, BuiltInRuleModuleIds.RIICHI, analysisOf(waiting("a", 4), waiting("b", 2)))

        assertEquals(listOf("a", "b"), content.cells.map { it.tileAssetKey })
        assertEquals(
            listOf(listOf(4), listOf(2)),
            content.cells.map { assertIs<TranslatableTextContent>(it.countText.content).args.toList() },
        )
    }

    /** 剩餘張數越少顏色越醒目。 */
    @Test
    fun `colours the count by how few tiles remain`() {
        val content = discardAnalysisContent(texts, BuiltInRuleModuleIds.RIICHI, analysisOf(waiting("a", 0), waiting("b", 1), waiting("c", 3)))

        assertEquals(3, content.cells.map { it.countColor }.distinct().size)
    }

    /** 沒有狀態指示與特殊和牌資格時沒有狀態列。 */
    @Test
    fun `builds no status line for a plain analysis`() {
        assertEquals(emptyList(), discardAnalysisContent(texts, BuiltInRuleModuleIds.RIICHI, analysisOf(waiting("a", 4))).statusTexts)
    }

    /** 分析本身的狀態指示佔一行狀態列。 */
    @Test
    fun `puts the status indicator on its own line`() {
        val content = discardAnalysisContent(
            texts,
            BuiltInRuleModuleIds.RIICHI,
            analysisOf(waiting("a", 4), statusIndicatorId = "mahjongcraft:discard_furiten"),
        )

        assertEquals(
            listOf("mahjongcraft.hud.furiten.discard"),
            content.statusTexts.map { assertIs<TranslatableTextContent>(it.content).key },
        )
    }

    /** 全部等待牌共用同一種特殊和牌資格時，資格提升到狀態列只講一次。 */
    @Test
    fun `lifts a shared availability into the status line`() {
        val content = discardAnalysisContent(
            texts,
            BuiltInRuleModuleIds.RIICHI,
            analysisOf(waiting("a", 4, "mahjongcraft:win_no_yaku"), waiting("b", 2, "mahjongcraft:win_no_yaku")),
        )

        assertEquals(
            listOf("mahjongcraft.hud.win_availability.no_yaku"),
            content.statusTexts.map { assertIs<TranslatableTextContent>(it.content).key },
        )
        assertFalse(content.hasAvailabilityRow)
        assertEquals(listOf(null, null), content.cells.map { it.availabilityText })
    }

    /** 全部等待牌都沒有特殊限制時不顯示任何和牌資格。 */
    @Test
    fun `shows no availability while every tile is unrestricted`() {
        val content = discardAnalysisContent(texts, BuiltInRuleModuleIds.RIICHI, analysisOf(waiting("a", 4), waiting("b", 2)))

        assertEquals(emptyList(), content.statusTexts)
        assertFalse(content.hasAvailabilityRow)
    }

    /** 和牌資格不一致時改為逐格標示，只標非預設的那幾張。 */
    @Test
    fun `marks each restricted tile when the availability differs`() {
        val content = discardAnalysisContent(
            texts,
            BuiltInRuleModuleIds.RIICHI,
            analysisOf(waiting("a", 4, "mahjongcraft:win_tsumo_only"), waiting("b", 2, WIN_AVAILABLE_ID)),
        )

        assertTrue(content.hasAvailabilityRow)
        assertEquals(
            "mahjongcraft.hud.win_availability.tsumo_only",
            assertIs<TranslatableTextContent>(content.cells[0].availabilityText?.content).key,
        )
        assertNull(content.cells[1].availabilityText)
        assertEquals(emptyList(), content.statusTexts)
    }

    /** 狀態指示與共用和牌資格同時存在時各佔一行。 */
    @Test
    fun `stacks the status indicator above a shared availability`() {
        val content = discardAnalysisContent(
            texts,
            BuiltInRuleModuleIds.RIICHI,
            analysisOf(waiting("a", 4, "mahjongcraft:win_no_yaku"), statusIndicatorId = "mahjongcraft:temporary_furiten"),
        )

        assertEquals(
            listOf("mahjongcraft.hud.furiten.temporary", "mahjongcraft.hud.win_availability.no_yaku"),
            content.statusTexts.map { assertIs<TranslatableTextContent>(it.content).key },
        )
    }

    /** 等待牌不多時排成一列。 */
    @Test
    fun `keeps a small analysis on one row`() {
        val layout = layout(cellCount = 4)

        assertEquals(4, layout.columns)
        assertEquals(1, layout.rowCount)
    }

    /** 超過每列上限時換行。 */
    @Test
    fun `wraps the cells past the column limit`() {
        val layout = layout(cellCount = 9)

        assertEquals(DiscardAnalysisLayout.MAX_COLUMNS, layout.columns)
        assertEquals(2, layout.rowCount)
    }

    /** 沒有等待牌時仍保留一欄一列，不會除以零。 */
    @Test
    fun `keeps one column for an empty analysis`() {
        val layout = layout(cellCount = 0)

        assertEquals(1, layout.columns)
        assertEquals(0, layout.rowCount)
    }

    /** 沒有狀態列時不保留狀態列高度。 */
    @Test
    fun `reserves no height without a status line`() {
        assertEquals(0, layout(cellCount = 3, statusLineCount = 0).statusHeight)
    }

    /** 狀態列增加一行就多一行的高度。 */
    @Test
    fun `grows the status area by one line at a time`() {
        assertEquals(
            layout(cellCount = 3, statusLineCount = 1).statusHeight + DiscardAnalysisLayout.STATUS_TEXT_HEIGHT,
            layout(cellCount = 3, statusLineCount = 2).statusHeight,
        )
    }

    /** 需要和牌資格那一行時格子變高。 */
    @Test
    fun `grows the cell height for the availability row`() {
        assertEquals(
            layout(cellCount = 3).cellHeight + DiscardAnalysisLayout.AVAILABILITY_HEIGHT,
            layout(cellCount = 3, hasAvailabilityRow = true).cellHeight,
        )
    }

    /** 面板寬度由欄數與量測到的最寬內容決定。 */
    @Test
    fun `sizes the panel from the widest cell content`() {
        val narrow = layout(cellCount = 3, widestCellContentWidth = 20)
        val wide = layout(cellCount = 3, widestCellContentWidth = 40)

        assertEquals(narrow.panelWidth + 3 * 20, wide.panelWidth)
    }

    /** 面板水平置中；寬度為奇數時左右留白最多差一個像素。 */
    @Test
    fun `centres the panel horizontally`() {
        val layout = layout(cellCount = 3, screenWidth = 640)

        val right = layout.panelLeft + layout.panelWidth
        assertTrue(layout.panelLeft - (640 - right) in -1..1)
        assertEquals(layout.panelLeft + layout.panelWidth / 2, layout.panelCenterX)
    }

    /** 位置比例 1 讓面板貼齊畫面下緣。 */
    @Test
    fun `anchors the panel to the bottom at ratio one`() {
        val layout = layout(cellCount = 3, ratioY = 1.0)

        assertEquals(layout.screenHeight - layout.panelHeight, layout.panelTop)
    }

    /** 格子由左至右、由上而下排列。 */
    @Test
    fun `lays the cells out row by row`() {
        val layout = layout(cellCount = 9)

        assertEquals(layout.tileBounds(0).y, layout.tileBounds(6).y)
        assertEquals(layout.tileBounds(0).y + layout.cellHeight, layout.tileBounds(7).y)
        assertEquals(layout.tileBounds(0).x, layout.tileBounds(7).x)
        assertEquals(layout.tileBounds(0).x + layout.cellWidth, layout.tileBounds(1).x)
    }

    /** 牌面在格內水平置中。 */
    @Test
    fun `centres the tile inside its cell`() {
        val layout = layout(cellCount = 3, widestCellContentWidth = 40)
        val tile = layout.tileBounds(0)

        assertEquals(layout.cellCenterX(0), tile.x + tile.width / 2)
    }

    /** 第一列格子落在狀態列下方。 */
    @Test
    fun `puts the first row below the status area`() {
        val layout = layout(cellCount = 3, statusLineCount = 2)

        assertTrue(layout.tileBounds(0).y > layout.dividerTop)
        assertEquals(layout.panelTop + DiscardAnalysisLayout.PADDING + layout.statusHeight, layout.tileBounds(0).y)
    }

    /** 剩餘張數接在牌面下方，和牌資格再接在剩餘張數下方。 */
    @Test
    fun `stacks the count and the availability below the tile`() {
        val layout = layout(cellCount = 3, hasAvailabilityRow = true)
        val tile = layout.tileBounds(0)

        assertEquals(tile.y + tile.height + 1, layout.countTextTop(0))
        assertTrue(layout.availabilityTextTop(0) > layout.countTextTop(0))
        assertTrue(layout.availabilityTextTop(0) + DiscardAnalysisLayout.AVAILABILITY_HEIGHT <= tile.y + layout.cellHeight)
    }

    /** 整個面板容納得下全部格子。 */
    @Test
    fun `fits every row inside the panel`() {
        val layout = layout(cellCount = 9, statusLineCount = 1, hasAvailabilityRow = true)
        val last = layout.tileBounds(8)

        assertTrue(last.y + layout.cellHeight <= layout.panelTop + layout.panelHeight - DiscardAnalysisLayout.PADDING)
    }

    /** 建立測試用的分析。 */
    private fun analysisOf(vararg waiting: WaitingTileAvailabilityDto, statusIndicatorId: String? = null) = DiscardReadinessAnalysisDto(
        discardTileId = "tile-1",
        waitingTiles = waiting.toList(),
        statusIndicatorId = statusIndicatorId,
    )

    /** 建立測試用的等待牌。 */
    private fun waiting(assetKey: String, remainingCount: Int, availability: String = WIN_AVAILABLE_ID) = WaitingTileAvailabilityDto(assetKey, remainingCount, availability)

    /** 建立測試用的版面。 */
    private fun layout(
        cellCount: Int,
        statusLineCount: Int = 0,
        widestCellContentWidth: Int = 24,
        hasAvailabilityRow: Boolean = false,
        screenWidth: Int = 854,
        screenHeight: Int = 480,
        ratioY: Double = 0.5,
    ) = DiscardAnalysisLayout(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        cellCount = cellCount,
        statusLineCount = statusLineCount,
        widestCellContentWidth = widestCellContentWidth,
        hasAvailabilityRow = hasAvailabilityRow,
        ratioY = ratioY,
    )
}
