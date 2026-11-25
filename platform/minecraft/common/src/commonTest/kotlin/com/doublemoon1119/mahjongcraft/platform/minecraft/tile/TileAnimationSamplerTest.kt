package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證不經過牌實體算出的動畫樣子，與牌實體播放同一份步驟的時間與軌跡相同。 */
class TileAnimationSamplerTest {
    /** 開始前停在原位。飛行途中與牌實體用同一套軌跡。播完停在目的地。 */
    @Test
    fun `flight follows the shared trajectory`() {
        val steps = PlannedTileMotionSteps.steps(motion(TileMotionKind.FLIGHT, TileRegion.HAND, TileRegion.DISCARD), START)
        val duration = MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS

        val before = TileAnimationSampler.sample(steps, FROM, TilePose.UPRIGHT, START - 1.0)
        val middle = TileAnimationSampler.sample(steps, FROM, TilePose.UPRIGHT, START + duration / 2.0)
        val after = TileAnimationSampler.sample(steps, FROM, TilePose.UPRIGHT, START + duration + 1.0)
        val frame = TileMotionAnimation(
            spec = TileMotionAnimationSpec(
                durationTicks = duration,
                arcHeight = MahjongTileTableLayout.DISCARD_ARC_HEIGHT,
                startPoseRotationDegrees = 0.0f,
                endPoseRotationDegrees = 90.0f,
            ),
        ).frame(duration / 2.0, DiceAnimationVector(FROM.x - TO.x, FROM.y - TO.y, FROM.z - TO.z))

        assertEquals(FROM, before.placement)
        assertEquals(TO.x + frame.offset.x, middle.placement.x)
        assertEquals(TO.y + frame.offset.y, middle.placement.y)
        assertEquals(frame.poseRotationDegrees, middle.poseDegrees)
        assertEquals(TO, after.placement)
        assertEquals(90.0f, after.poseDegrees)
    }

    /** 摸牌抬起後隱形，等一下才在目的地出現並落下。聲音只在落地那一刻。 */
    @Test
    fun `draw hides between lifting and dropping`() {
        val steps = PlannedTileMotionSteps.steps(motion(TileMotionKind.DRAW, TileRegion.WALL, TileRegion.DRAWN), START)
        val hiddenAt = START + MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS + MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS / 2.0
        val end = START + TableStepMotionPlanner.durationTicks(TileMotionKind.DRAW).toDouble()

        assertTrue(TileAnimationSampler.sample(steps, FROM, TilePose.FACE_DOWN, hiddenAt).invisible)
        assertFalse(TileAnimationSampler.sample(steps, FROM, TilePose.FACE_DOWN, end).invisible)
        assertEquals(end, TileAnimationSampler.endTime(steps))
        assertEquals(1, TileAnimationSampler.sounds(steps, after = START.toDouble(), upTo = end).size)
        assertTrue(TileAnimationSampler.sounds(steps, after = end, upTo = end + 10.0).isEmpty())
    }

    /**
     * 建立一個動作。
     *
     * @param kind 動作種類。
     * @param fromRegion 起點區域。
     * @param toRegion 終點區域。
     * @return 動作。
     */
    private fun motion(kind: TileMotionKind, fromRegion: TileRegion, toRegion: TileRegion): PlannedTileMotion = PlannedTileMotion(
        tileId = Uuid.random(),
        kind = kind,
        fromRow = TileRowKey(fromRegion, null, null),
        toRow = TileRowKey(toRegion, null, null),
        from = FROM,
        to = TO,
        fromPose = if (kind == TileMotionKind.DRAW) TilePose.FACE_DOWN else TilePose.UPRIGHT,
        toPose = if (kind == TileMotionKind.DRAW) TilePose.UPRIGHT else TilePose.FACE_UP,
    )

    /** 測試共用的位置與時間。 */
    private companion object {
        /** 動作開始的遊戲 tick。 */
        const val START = 40L

        /** 牌原本的位置。 */
        val FROM = MahjongTileWallPlacement(1.0, 1.1, 2.0, 90.0f)

        /** 牌的目的地。 */
        val TO = MahjongTileWallPlacement(0.5, 1.0, 0.5, 0.0f)
    }
}
