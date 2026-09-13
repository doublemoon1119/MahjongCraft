package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileWallMotionPhasePlan
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileWallMotionSegment
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileWallPhaseMotion
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileWallTransitionMotionPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證牌牆路徑轉換成持久化 Fabric 動畫步驟時的同步邊界。 */
class TileWallTransitionAnimationCompilerTest {
    /** 同一 phase 共同開始，下一 phase 應等待前一 phase 的最長路徑。 */
    @Test
    fun `compiler applies longest phase barrier`() {
        val firstTileId = Uuid.random()
        val secondTileId = Uuid.random()
        val plan = TileWallTransitionMotionPlan(
            listOf(
                TileWallMotionPhasePlan(
                    listOf(
                        motion(firstTileId, segment(4)),
                        motion(secondTileId, segment(3), segment(5)),
                    ),
                ),
                TileWallMotionPhasePlan(listOf(motion(firstTileId, segment(2, startX = 1.0)))),
            ),
        )

        val compiled = TileWallTransitionAnimationCompiler.compile(
            plan,
            START_GAME_TIME,
            mapOf(firstTileId to MahjongTilePose.FACE_DOWN, secondTileId to MahjongTilePose.FACE_UP),
        )

        val firstWaits = compiled.getValue(firstTileId).filterIsInstance<AnimationStep.WaitUntil>()
        val secondWaits = compiled.getValue(secondTileId).filterIsInstance<AnimationStep.WaitUntil>()
        assertEquals(listOf(START_GAME_TIME, START_GAME_TIME + 8), firstWaits.map { it.gameTime })
        assertEquals(listOf(START_GAME_TIME), secondWaits.map { it.gameTime })
    }

    /** 每段路徑應保留真實終點、相對起點、最短 yaw 與原本牌面姿態。 */
    @Test
    fun `compiler preserves segment geometry and pose`() {
        val tileId = Uuid.random()
        val source = placement(1.0, 2.0, 3.0, 350.0f)
        val destination = placement(4.0, 5.0, 6.0, 10.0f)
        val plan = TileWallTransitionMotionPlan(
            listOf(TileWallMotionPhasePlan(listOf(motion(tileId, TileWallMotionSegment(source, destination, 7))))),
        )

        val steps = TileWallTransitionAnimationCompiler.compile(
            plan,
            START_GAME_TIME,
            mapOf(tileId to MahjongTilePose.STANDING),
        ).getValue(tileId)

        val teleport = assertIs<AnimationStep.Teleport>(steps[1])
        val motion = assertIs<AnimationStep.PlayMotion>(steps[2])
        assertEquals(destination.x, teleport.x)
        assertEquals(destination.y, teleport.y)
        assertEquals(destination.z, teleport.z)
        assertEquals(destination.yaw, teleport.yaw)
        assertEquals(source.x - destination.x, motion.startOffsetX)
        assertEquals(source.y - destination.y, motion.startOffsetY)
        assertEquals(source.z - destination.z, motion.startOffsetZ)
        assertEquals(-20.0f, motion.startYawOffsetDegrees)
        assertEquals(MahjongTilePose.STANDING.rotationDegrees, motion.startPoseRotationDegrees)
        assertEquals(MahjongTilePose.STANDING.rotationDegrees, motion.endPoseRotationDegrees)
    }

    /** 空 plan 不應建立任何牌張排程。 */
    @Test
    fun `empty plan compiles to empty schedule`() {
        assertTrue(
            TileWallTransitionAnimationCompiler.compile(
                TileWallTransitionMotionPlan(emptyList()),
                START_GAME_TIME,
                emptyMap(),
            ).isEmpty(),
        )
    }

    /** 缺少任何牌的初始姿態時應在產生部分排程前拒絕整批輸入。 */
    @Test
    fun `missing initial pose rejects whole plan`() {
        val tileId = Uuid.random()
        val plan = TileWallTransitionMotionPlan(
            listOf(TileWallMotionPhasePlan(listOf(motion(tileId, segment(2))))),
        )

        assertFailsWith<IllegalArgumentException> {
            TileWallTransitionAnimationCompiler.compile(plan, START_GAME_TIME, emptyMap())
        }
    }

    /** 建立一張牌在單一 phase 內的測試路徑。 */
    private fun motion(tileId: Uuid, vararg segments: TileWallMotionSegment): TileWallPhaseMotion = TileWallPhaseMotion(tileId, segments.toList())

    /** 建立沿 X 軸移動的測試路徑段。 */
    private fun segment(durationTicks: Int, startX: Double = 0.0): TileWallMotionSegment = TileWallMotionSegment(
        placement(startX, 0.0, 0.0, 0.0f),
        placement(startX + 1.0, 0.0, 0.0, 0.0f),
        durationTicks,
    )

    /** 建立測試用世界 placement。 */
    private fun placement(x: Double, y: Double, z: Double, yaw: Float): MahjongTileWallPlacement = MahjongTileWallPlacement(x, y, z, yaw)

    /** 測試共用常數。 */
    private companion object {
        const val START_GAME_TIME: Long = 200L
    }
}
