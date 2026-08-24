package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證 TNT 役滿演出的固定 phase 邊界與牌組 stagger。 */
class WinCelebrationCinematicTimelineTest {
    @Test
    fun `phases remain ordered and showcase starts at tick 300`() {
        val starts = listOf(
            WinCelebrationCinematicTimeline.PREPARATION_START,
            WinCelebrationCinematicTimeline.LAUNCH_START,
            WinCelebrationCinematicTimeline.ORBIT_BUILDUP_START,
            WinCelebrationCinematicTimeline.TNT_APPROACH_START,
            WinCelebrationCinematicTimeline.TNT_PLACEMENT_START,
            WinCelebrationCinematicTimeline.TOOL_SWAP_START,
            WinCelebrationCinematicTimeline.IGNITION_START,
            WinCelebrationCinematicTimeline.FUSE_START,
            WinCelebrationCinematicTimeline.EXPLOSION_START,
            WinCelebrationCinematicTimeline.FORMATION_RETURN_START,
            WinCelebrationCinematicTimeline.TITLE_REVEAL_START,
            WinCelebrationCinematicTimeline.SHOWCASE_START,
        )
        assertTrue(starts.zipWithNext().all { (left, right) -> left < right })
        assertEquals(300.0, WinCelebrationCinematicTimeline.SHOWCASE_START)
        assertEquals(478L, WinCelebrationCinematicTimeline.totalDurationTicks(160))
    }

    @Test
    fun `formation return starts with the blast and settles before title reveal`() {
        val returnTicks = (0 until 43).map { WinCelebrationCinematicTimeline.returnStartTick(it, 43) }
        assertTrue(returnTicks.zipWithNext().all { (left, right) -> left <= right })
        assertTrue(returnTicks.first() >= WinCelebrationCinematicTimeline.FORMATION_RETURN_START)
        assertEquals(220.0, returnTicks.last())
        assertEquals(260.0, returnTicks.last() + 40.0)
        assertTrue(returnTicks.last() + 40.0 < WinCelebrationCinematicTimeline.TITLE_REVEAL_START)
    }
}
