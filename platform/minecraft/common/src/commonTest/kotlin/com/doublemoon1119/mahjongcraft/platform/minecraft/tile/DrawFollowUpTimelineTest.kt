package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證摸牌後自動處理的逐步時間軸。 */
class DrawFollowUpTimelineTest {
    /** 第一步等第一張牌落地並停頓後才開始；有副露或移出動畫時，第一次摸牌再往後延。 */
    @Test
    fun `first step starts after the first draw lands and pauses`() {
        val landing = DrawFollowUpTimeline.DRAW_DURATION_TICKS

        assertEquals(landing + DrawFollowUpTimeline.STEP_PAUSE_TICKS, DrawFollowUpTimeline.stepStartTicks(1, hasClaimAnimation = false).single())
        assertEquals(
            MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS + landing + DrawFollowUpTimeline.STEP_PAUSE_TICKS,
            DrawFollowUpTimeline.stepStartTicks(1, hasClaimAnimation = true).single(),
        )
    }

    /** 每一步等上一步補到的牌落地後才開始，步與步之間不重疊。 */
    @Test
    fun `each step waits for the previous replacement to land`() {
        val starts = DrawFollowUpTimeline.stepStartTicks(3, hasClaimAnimation = false)

        starts.zipWithNext().forEach { (previous, next) ->
            val previousLanding = DrawFollowUpTimeline.replacementDrawStartTicks(previous) + DrawFollowUpTimeline.DRAW_DURATION_TICKS
            assertEquals(previousLanding + DrawFollowUpTimeline.STEP_PAUSE_TICKS, next)
            assertTrue(next > previousLanding, "Steps must not overlap")
        }
    }

    /** 補牌在移出的牌落地之後才離開牌牆。 */
    @Test
    fun `replacement draw starts after the set aside tile lands`() {
        assertEquals(10L + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS, DrawFollowUpTimeline.replacementDrawStartTicks(10L))
    }

    /** 沒有步驟時沒有時刻；步數不可為負。 */
    @Test
    fun `empty and negative step counts`() {
        assertTrue(DrawFollowUpTimeline.stepStartTicks(0, hasClaimAnimation = false).isEmpty())
        assertFailsWith<IllegalArgumentException> { DrawFollowUpTimeline.stepStartTicks(-1, hasClaimAnimation = false) }
    }
}
