package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileWallMotionSegment
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileWallTransitionMotionPlan
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.shortestYawDeltaDegrees
import kotlin.uuid.Uuid

/** 將已驗證的牌牆世界路徑編譯為各牌共用 phase barrier 的持久化動畫步驟。 */
object TileWallTransitionAnimationCompiler {
    /**
     * 依 [startGameTime] 編譯完整 [plan]；[initialPoses] 必須涵蓋所有會移動的牌。
     * 任一姿態缺失時先拒絕整批輸入，不回傳部分排程。
     *
     * @param plan 已驗證的牌牆移動階段。
     * @param startGameTime 第一階段開始的絕對遊戲時間。
     * @param initialPoses 每張移動牌開始時的姿態。
     * @return 依牌 UUID 分組的持久化動畫步驟。
     */
    fun compile(
        plan: TileWallTransitionMotionPlan,
        startGameTime: Long,
        initialPoses: Map<Uuid, MahjongTilePose>,
    ): Map<Uuid, List<AnimationStep<MahjongTilePose>>> {
        val tileIds = plan.phases.flatMapTo(mutableSetOf()) { phase -> phase.motions.map { it.tileId } }
        require(initialPoses.keys.containsAll(tileIds)) { "Every wall motion tile must have an initial pose" }
        if (tileIds.isEmpty()) return emptyMap()

        val stepsByTile = tileIds.associateWith { mutableListOf<AnimationStep<MahjongTilePose>>() }
        var phaseStart = startGameTime
        plan.phases.forEach { phase ->
            phase.motions.forEach { motion ->
                val steps = stepsByTile.getValue(motion.tileId)
                steps += AnimationStep.WaitUntil(phaseStart)
                steps += compileMotion(motion.segments, initialPoses.getValue(motion.tileId))
            }
            phaseStart += phase.durationTicks
        }
        return stepsByTile
    }

    /**
     * 將同一張牌依序經過的 [segments] 編譯為既有持久化動畫步驟。
     *
     * @param segments 此牌依序經過的路徑片段。
     * @param pose 動畫期間維持的牌姿態。
     * @return 對應的動畫步驟。
     */
    fun compileMotion(
        segments: List<TileWallMotionSegment>,
        pose: MahjongTilePose,
    ): List<AnimationStep<MahjongTilePose>> = segments.flatMap { segment -> segment.toAnimationSteps(pose) }

    /**
     * 將一段真實世界路徑轉成「設定終點後由起點 offset 播放」的既有 motion 格式。
     *
     * @param pose 此路徑保持的牌姿態。
     * @return 先定位終點再由起點 offset 播放的步驟。
     */
    private fun TileWallMotionSegment.toAnimationSteps(
        pose: MahjongTilePose,
    ): List<AnimationStep<MahjongTilePose>> = listOf(
        AnimationStep.Teleport(end.x, end.y, end.z, end.yaw),
        AnimationStep.PlayMotion(
            durationTicks = durationTicks,
            arcHeight = 0.0,
            startOffsetX = start.x - end.x,
            startOffsetY = start.y - end.y,
            startOffsetZ = start.z - end.z,
            startPoseRotationDegrees = pose.rotationDegrees,
            endPoseRotationDegrees = pose.rotationDegrees,
            startYawOffsetDegrees = shortestYawDeltaDegrees(end.yaw, start.yaw),
        ),
    )
}
