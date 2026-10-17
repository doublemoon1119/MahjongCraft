package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationQueueDriver
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證攤牌排序僅在指定時刻定位，不插入滑動或提早改變牌姿態。 */
class TileRevealReorderTest {
    /** 排序步驟不依賴原生世界，可直接檢查共用的動畫佇列。 */
    @Test
    fun `reveal reorder waits and teleports without motion or pose changes`() {
        val placement = MahjongTileWallPlacement(x = 1.0, y = 2.0, z = 3.0, yaw = 90.0f)
        val steps = TileAnimationSteps.instantReorderSteps(placement, startGameTime = 100L)

        assertEquals(
            listOf(AnimationStep.WaitUntil(100L), AnimationStep.Teleport(1.0, 2.0, 3.0, 90.0f)),
            steps,
        )
        val waiting = AnimationQueueDriver.tick(steps, null, 99L)
        assertTrue(waiting.appliedInstantSteps.isEmpty())

        val due = AnimationQueueDriver.tick(waiting.remainingQueue, waiting.activeStepEndGameTime, 100L)
        assertEquals(listOf(AnimationStep.Teleport(1.0, 2.0, 3.0, 90.0f)), due.appliedInstantSteps)
        assertNull(due.startedPlayMotion)
        assertTrue(due.remainingQueue.isEmpty())
    }

    /** 瞬間排序後，倒牌或蓋牌步驟會等待指定的展示時間。 */
    @Test
    fun `instant reorder preserves the following pose animation barrier`() {
        val placement = MahjongTileWallPlacement(x = 1.0, y = 2.0, z = 3.0, yaw = 90.0f)
        listOf(MahjongTilePose.FACE_UP, MahjongTilePose.FACE_DOWN).forEach { pose ->
            val poseMotion = AnimationStep.PlayMotion(
                durationTicks = 4,
                arcHeight = 0.0,
                startOffsetX = 0.0,
                startOffsetY = 0.0,
                startOffsetZ = 0.0,
                startPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                endPoseRotationDegrees = pose.rotationDegrees,
            )
            val steps = TileAnimationSteps.instantReorderSteps(placement, startGameTime = 100L) + listOf(
                AnimationStep.WaitUntil(130L),
                AnimationStep.Custom(pose),
                poseMotion,
            )
            val reordered = AnimationQueueDriver.tick(steps, null, 100L)
            assertNull(reordered.startedPlayMotion)
            val laidDown = AnimationQueueDriver.tick(reordered.remainingQueue, reordered.activeStepEndGameTime, 130L)
            assertEquals(listOf(AnimationStep.Custom(pose)), laidDown.appliedInstantSteps)
            assertEquals(poseMotion, laidDown.startedPlayMotion)
        }
    }
}
