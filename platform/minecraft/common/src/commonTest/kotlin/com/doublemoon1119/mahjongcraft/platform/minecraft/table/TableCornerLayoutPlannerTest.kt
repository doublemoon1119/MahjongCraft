package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    /** 平移途中的更新即使寬度沒變也要等平移結束才擺物件，並接手等待；先前那次更新的等待不再擺物件。 */
    @Test
    fun `an update during a slide waits for the slide and takes it over`() = runTest {
        val planner = plannerWithRound(mapOf(0 to 0.0))
        val widening = planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK))

        val during = planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK))

        assertTrue(during.slidingSeatWidths.isEmpty())
        assertTrue(during.presentsPropsAfterSlide)
        assertFalse(planner.awaitSettled(widening.slideId))
        assertTrue(planner.awaitSettled(during.slideId))
    }

    /** 平移結束後的更新不再等待。 */
    @Test
    fun `an update after the slide finished presents the props at once`() = runTest {
        val planner = plannerWithRound(mapOf(0 to 0.0))
        val widening = planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK))
        assertTrue(planner.awaitSettled(widening.slideId))

        val after = planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK))

        assertFalse(after.presentsPropsAfterSlide)
        assertNull(after.slideId)
    }

    /** 平移途中變得比途中用過的每個寬度都窄時，立刻收走物件。 */
    @Test
    fun `narrowing below every width used during a slide presents the props at once`() {
        val planner = plannerWithRound(mapOf(0 to STICK))
        planner.planRuleStateUpdate(tableId, state, mapOf(0 to 2 * STICK))

        val narrowing = planner.planRuleStateUpdate(tableId, state, mapOf(0 to 0.0))

        assertEquals(mapOf(0 to 0.0), narrowing.slidingSeatWidths)
        assertFalse(narrowing.presentsPropsAfterSlide)
    }

    /** 平移途中變回比途中最窄寬度還寬時，仍要等平移結束。 */
    @Test
    fun `widening again during a narrowing slide waits for the slide`() {
        val planner = plannerWithRound(mapOf(0 to STICK))
        planner.planRuleStateUpdate(tableId, state, mapOf(0 to 0.0))

        assertTrue(planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK)).presentsPropsAfterSlide)
    }

    /** 牌一直在動時，超過提醒的 tick 數也不結束平移，只提醒一次；牌停下後才結束。 */
    @Test
    fun `a slow slide keeps waiting until the tiles stop`() = runTest {
        val planner = plannerWithRound(mapOf(0 to 0.0))
        val slideId = assertNotNull(planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK)).slideId)
        val movingTicks = TableCornerLayoutPlanner.SLOW_SLIDE_WARNING_TICKS + EXTRA_MOVING_TICKS
        var waitedTicks = 0
        var warnings = 0

        val ownsSlide = planner.awaitSlideEnd(
            tableId = tableId,
            slideId = slideId,
            areTilesMoving = { waitedTicks < movingTicks },
            waitOneTick = { waitedTicks++ },
            onSlowSlide = { warnings++ },
        )

        assertTrue(ownsSlide)
        assertEquals(movingTicks, waitedTicks)
        assertEquals(1, warnings)
    }

    /** 等待途中被新的更新接手時，舊的等待立刻停止且不擺物件，由新的等待擺上。 */
    @Test
    fun `an update taking over during the wait stops the old wait`() = runTest {
        val planner = plannerWithRound(mapOf(0 to 0.0))
        val widening = assertNotNull(planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK)).slideId)
        var takeover: CornerPropsUpdatePlan? = null

        val oldOwnsSlide = planner.awaitSlideEnd(
            tableId = tableId,
            slideId = widening,
            areTilesMoving = { true },
            waitOneTick = {
                check(takeover == null) { "kept waiting after another update took over" }
                takeover = planner.planRuleStateUpdate(tableId, state, mapOf(0 to 2 * STICK))
            },
            onSlowSlide = {},
        )

        assertFalse(oldOwnsSlide)
        assertTrue(planner.awaitSettled(assertNotNull(takeover).slideId))
    }

    /** 檢查牌的途中被新的更新接手時，即使牌已停下，舊的等待也不擺物件。 */
    @Test
    fun `an update arriving while the tiles are checked takes over`() = runTest {
        val planner = plannerWithRound(mapOf(0 to 0.0))
        val widening = assertNotNull(planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK)).slideId)
        var takeover: CornerPropsUpdatePlan? = null

        val oldOwnsSlide = planner.awaitSlideEnd(
            tableId = tableId,
            slideId = widening,
            areTilesMoving = {
                takeover = planner.planRuleStateUpdate(tableId, state, mapOf(0 to 2 * STICK))
                false
            },
            waitOneTick = { error("kept waiting after the tiles stopped") },
            onSlowSlide = {},
        )

        assertFalse(oldOwnsSlide)
        assertTrue(planner.awaitSettled(assertNotNull(takeover).slideId))
    }

    /** 等待途中換局、清桌或對局已不存在時停止等待，不擺物件。 */
    @Test
    fun `a new round, a cleared table or a vanished game stops the wait`() = runTest {
        val nextRound = state.copy(comboCount = state.comboCount + 1)
        val stops = listOf<suspend (TableCornerLayoutPlanner) -> Unit>(
            { planner -> planner.recordRound(tableId, nextRound, mapOf(0 to 0.0)) },
            { planner -> planner.clear(tableId) },
        )
        stops.forEach { stop ->
            val planner = plannerWithRound(mapOf(0 to 0.0))
            val slideId = assertNotNull(planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK)).slideId)
            var stopped = false

            val ownsSlide = planner.awaitSlideEnd(
                tableId = tableId,
                slideId = slideId,
                areTilesMoving = { true },
                waitOneTick = {
                    check(!stopped) { "kept waiting after the slide was dropped" }
                    stop(planner)
                    stopped = true
                },
                onSlowSlide = {},
            )

            assertFalse(ownsSlide)
        }
        val planner = plannerWithRound(mapOf(0 to 0.0))
        val slideId = assertNotNull(planner.planRuleStateUpdate(tableId, state, mapOf(0 to STICK)).slideId)

        val ownsSlide = planner.awaitSlideEnd(
            tableId = tableId,
            slideId = slideId,
            areTilesMoving = { null },
            waitOneTick = { error("kept waiting after the game was gone") },
            onSlowSlide = {},
        )

        assertFalse(ownsSlide)
    }

    /**
     * 等待牌立刻停下的平移。
     *
     * @param slideId 那次更新拿到的平移識別碼。
     * @return 這次更新是否仍負責這次平移。
     */
    private suspend fun TableCornerLayoutPlanner.awaitSettled(slideId: Long?): Boolean = awaitSlideEnd(tableId, assertNotNull(slideId), areTilesMoving = { false }, waitOneTick = {}, onSlowSlide = {})

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

        /** 超過提醒的 tick 數之後牌還繼續動的 tick 數。 */
        const val EXTRA_MOVING_TICKS: Int = 50
    }
}
