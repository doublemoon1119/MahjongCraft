package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證桌角寬度改變時，擺牌區用的寬度、需要平移的座位與物件擺放時機。 */
class TableCornerLayoutPlannerTest {
    /** 測試桌子。 */
    private val tableId = Uuid.random()

    /** 這一局的桌況。 */
    private val state = FakeTableStateFactory.create()

    /** 剛擺好的座位已經依新寬度讓開，物件同步時不再移動，只有其他已擺過的座位一起平移，並等平移結束才擺上物件。 */
    @Test
    fun `a widening corner slides only the seats laid out with the old width`() {
        val planner = plannerWithRound(mapOf(0 to 0.0, 1 to 0.0))

        assertEquals(STICK, planner.widthForPlayerArea(tableId, state, seatIndex = 0, currentWidthsBySeat = mapOf(0 to STICK, 1 to STICK)))
        val plan = planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK, 1 to STICK))

        assertEquals(mapOf(1 to STICK), plan.slidingSeatWidths)
        assertTrue(plan.presentsPropsAfterSlide)
    }

    /** 物件變少時先收走物件再平移。 */
    @Test
    fun `a narrowing corner removes the props before sliding`() {
        val planner = plannerWithRound(mapOf(0 to STICK))

        val plan = planner.planRuleStateUpdate(tableId, state, mapOf(0 to 0.0))

        assertEquals(mapOf(0 to 0.0), plan.slidingSeatWidths)
        assertFalse(plan.presentsPropsAfterSlide)
    }

    /** 物件已不在權威桌況但還沒從桌上收走時，擺牌區維持原本的寬度。 */
    @Test
    fun `a player area keeps clear of props still on the table`() {
        val planner = plannerWithRound(mapOf(0 to STICK))

        assertEquals(STICK, planner.widthForPlayerArea(tableId, state, seatIndex = 0, currentWidthsBySeat = mapOf(0 to 0.0)))
    }

    /** 換局後上一局的寬度不再拿來比較，不會移動任何座位；這一局還沒擺過的座位也不移動。 */
    @Test
    fun `seats from another round or not laid out yet do not slide`() {
        val planner = plannerWithRound(mapOf(0 to 0.0))
        val nextRound = state.copy(comboCount = state.comboCount + 1)

        assertTrue(planner.planRuleStateUpdate(tableId, nextRound, mapOf(0 to STICK)).slidingSeatWidths.isEmpty())
        assertTrue(planner.planRuleStateUpdate(tableId, nextRound, mapOf(0 to STICK, 1 to STICK)).slidingSeatWidths.isEmpty())
    }

    /**
     * 建立已記下這一局寬度的 planner。
     *
     * @param widths 這一局開局時各座位的寬度。
     * @return planner。
     */
    private fun plannerWithRound(widths: Map<Int, Double>) = TableCornerLayoutPlanner(TableCornerWidthTracker()).apply {
        recordRound(tableId, state, widths)
    }

    private companion object {
        /** 一根點棒佔用的寬度。 */
        const val STICK: Double = 0.065
    }
}
