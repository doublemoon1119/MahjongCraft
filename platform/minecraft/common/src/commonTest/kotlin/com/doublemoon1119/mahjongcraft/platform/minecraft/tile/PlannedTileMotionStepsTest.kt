package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.MahjongAnimationSounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證規劃好的牌動作展開成的動畫步驟，與對局中牌實體播放的順序、時間和聲音相同。 */
class PlannedTileMotionStepsTest {
    /** 摸牌：原地抬起、隱形移到目的地、等一下再出現並落下，落地播放摸牌聲。 */
    @Test
    fun `draw lifts hides moves and drops with the draw sound`() {
        val motion = motion(TileMotionKind.DRAW, TileRegion.WALL, TileRegion.DRAWN, TilePose.FACE_DOWN, TilePose.UPRIGHT)
        val peakY = FROM.y + MahjongTileTableLayout.DRAW_LIFT_HEIGHT
        val end = START + TableStepMotionPlanner.durationTicks(TileMotionKind.DRAW)

        assertEquals(
            expected = listOf(
                AnimationStep.WaitUntil(START),
                AnimationStep.Teleport(FROM.x, peakY, FROM.z, FROM.yaw),
                AnimationStep.Custom(TilePose.FACE_DOWN),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = FROM.y - peakY,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = -90.0f,
                    endPoseRotationDegrees = -90.0f,
                ),
                AnimationStep.SetInvisible(true),
                AnimationStep.Teleport(TO.x, TO.y, TO.z, TO.yaw),
                AnimationStep.Custom(TilePose.UPRIGHT),
                AnimationStep.WaitUntil(START + MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS + MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS),
                AnimationStep.SetInvisible(false),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = peakY - TO.y,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = 0.0f,
                    endPoseRotationDegrees = 0.0f,
                ),
                AnimationStep.PlaySound(MahjongAnimationSounds.DRAW_TILE_LAND, 0.35f, 1.1f, end, end + MahjongAnimationSounds.EVENT_GRACE_TICKS),
            ),
            actual = PlannedTileMotionSteps.steps(motion, START),
        )
    }

    /** 飛到牌河是一段拋物線，落地播放捨牌聲。飛到副露區播放副露聲。不播聲音時沒有聲音步驟。 */
    @Test
    fun `flights arc to the destination with the landing sound of the area`() {
        val discard = PlannedTileMotionSteps.steps(motion(TileMotionKind.FLIGHT, TileRegion.HAND, TileRegion.DISCARD, TilePose.UPRIGHT, TilePose.FACE_UP), START)
        val meld = PlannedTileMotionSteps.steps(motion(TileMotionKind.FLIGHT, TileRegion.DISCARD, TileRegion.MELD, TilePose.FACE_UP, TilePose.FACE_UP), START)
        val silent = PlannedTileMotionSteps.steps(motion(TileMotionKind.FLIGHT, TileRegion.HAND, TileRegion.DISCARD, TilePose.UPRIGHT, TilePose.FACE_UP), START, playLandingSound = false)

        val flight = discard.filterIsInstance<AnimationStep.PlayMotion>().single()
        assertEquals(MahjongTileTableLayout.DISCARD_ARC_HEIGHT, flight.arcHeight)
        assertEquals(FROM.x - TO.x, flight.startOffsetX)
        assertEquals(0.0f to 90.0f, flight.startPoseRotationDegrees to flight.endPoseRotationDegrees)
        assertEquals(MahjongAnimationSounds.TILE_DISCARD_LAND, discard.filterIsInstance<AnimationStep.PlaySound>().single().soundId)
        assertEquals(MahjongAnimationSounds.TILE_MELD_LAND, meld.filterIsInstance<AnimationStep.PlaySound>().single().soundId)
        assertTrue(silent.none { it is AnimationStep.PlaySound })
    }

    /** 立著越過其他牌的弧線往前傾。平移、翻面與直接擺放沒有落地聲。 */
    @Test
    fun `upright arcs lean and row moves are silent`() {
        val arc = PlannedTileMotionSteps.steps(motion(TileMotionKind.ARC, TileRegion.HAND, TileRegion.HAND, TilePose.UPRIGHT, TilePose.UPRIGHT), START)
        val kinds = listOf(TileMotionKind.SLIDE, TileMotionKind.TURN, TileMotionKind.PLACE)

        val motion = arc.filterIsInstance<AnimationStep.PlayMotion>().single()
        assertTrue(motion.liftCarryLower)
        assertEquals(MahjongTileTableLayout.ROW_ARC_LEAN_DEGREES, motion.leanDegrees)
        kinds.forEach { kind ->
            assertTrue(PlannedTileMotionSteps.steps(motion(kind, TileRegion.HAND, TileRegion.HAND, TilePose.UPRIGHT, TilePose.FACE_UP), START).none { it is AnimationStep.PlaySound })
        }
    }

    /** 一步之中先離開、再讓位、最後摸牌落下。一起落到同一排的牌只有一張播放落地聲。 */
    @Test
    fun `a step starts each phase after the previous one and plays one sound per landing row`() {
        val discard = motion(TileMotionKind.FLIGHT, TileRegion.HAND, TileRegion.DISCARD, TilePose.UPRIGHT, TilePose.FACE_UP)
        val slide = motion(TileMotionKind.SLIDE, TileRegion.HAND, TileRegion.HAND, TilePose.UPRIGHT, TilePose.UPRIGHT)
        val melds = List(2) { motion(TileMotionKind.FLIGHT, TileRegion.HAND, TileRegion.MELD, TilePose.UPRIGHT, TilePose.FACE_UP) }

        val steps = PlannedTileMotionSteps.forStep(listOf(discard, slide) + melds, START)

        assertEquals(AnimationStep.WaitUntil(START), steps.getValue(discard.tileId).first())
        assertEquals(AnimationStep.WaitUntil(START + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS), steps.getValue(slide.tileId).first())
        assertEquals(1, melds.count { meld -> steps.getValue(meld.tileId).any { it is AnimationStep.PlaySound } })
    }

    /**
     * 建立一個動作。
     *
     * @param kind 動作種類。
     * @param fromRegion 起點區域。
     * @param toRegion 終點區域。
     * @param fromPose 起點姿態。
     * @param toPose 終點姿態。
     * @return 動作。
     */
    private fun motion(kind: TileMotionKind, fromRegion: TileRegion, toRegion: TileRegion, fromPose: TilePose, toPose: TilePose): PlannedTileMotion = PlannedTileMotion(
        tileId = Uuid.random(),
        kind = kind,
        fromRow = TileRowKey(fromRegion, OWNER, null),
        toRow = TileRowKey(toRegion, OWNER, null),
        from = FROM,
        to = TO,
        fromPose = fromPose,
        toPose = toPose,
    )

    /** 測試共用的位置與時間。 */
    private companion object {
        /** 動作開始的遊戲 tick。 */
        const val START = 100L

        /** 牌原本的位置。 */
        val FROM = MahjongTileWallPlacement(1.0, 1.1, 2.0, 90.0f)

        /** 牌的目的地。 */
        val TO = MahjongTileWallPlacement(0.5, 1.0, 0.5, 0.0f)

        /** 牌列的主人。 */
        val OWNER: Uuid = Uuid.random()
    }
}
