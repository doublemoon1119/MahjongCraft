package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector

/**
 * 不經過牌實體，直接算出一張牌依動畫步驟播放到某一刻的樣子。牌譜回放的場景用它播放和對局中相同的步驟。
 *
 * 步驟的時間語意與牌實體的動畫佇列相同：
 *
 * - 等待步驟等到指定時刻。
 * - 動作步驟從前一個步驟完成的時刻開始，播完才往下走。
 * - 瞬間步驟（瞬移、姿態、隱形）在輪到時立即生效。
 */
object TileAnimationSampler {
    /**
     * 算出某一刻的樣子。
     *
     * @param steps 依序播放的步驟。
     * @param start 開始播放前的位置。
     * @param startPose 開始播放前的姿態。
     * @param time 要算的時刻，與步驟中的遊戲 tick 同一個時間軸，可以有小數。
     * @return 這一刻的位置、姿態、傾斜與是否隱形。
     */
    fun sample(
        steps: List<AnimationStep<TilePose>>,
        start: MahjongTileWallPlacement,
        startPose: TilePose,
        time: Double,
    ): TileAnimationSample {
        var placement = start
        var pose = startPose
        var invisible = false
        var cursor = Double.NEGATIVE_INFINITY
        steps.forEach { step ->
            when (step) {
                is AnimationStep.WaitUntil -> {
                    if (time < step.gameTime) return TileAnimationSample(placement, pose.rotationDegrees, TileLean.NONE, invisible)
                    cursor = maxOf(cursor, step.gameTime.toDouble())
                }
                is AnimationStep.Teleport -> placement = MahjongTileWallPlacement(step.x, step.y, step.z, step.yaw)
                is AnimationStep.Custom -> pose = step.step
                is AnimationStep.SetInvisible -> invisible = step.invisible
                is AnimationStep.PlaySound -> Unit
                is AnimationStep.PlayMotion -> {
                    val motionStart = if (cursor.isInfinite()) time else cursor
                    val end = motionStart + step.durationTicks
                    if (time < end) return motionSample(step, placement, invisible, time - motionStart)
                    cursor = end
                }
            }
        }
        return TileAnimationSample(placement, pose.rotationDegrees, TileLean.NONE, invisible)
    }

    /**
     * 步驟中預定在一段時間內播放的聲音。
     *
     * @param steps 依序播放的步驟。
     * @param after 不含的起點時刻。
     * @param upTo 包含的終點時刻。
     * @return 播放時刻落在這段時間內的聲音。
     */
    fun sounds(steps: List<AnimationStep<TilePose>>, after: Double, upTo: Double): List<AnimationStep.PlaySound> = steps.filterIsInstance<AnimationStep.PlaySound>().filter { it.playAtGameTime > after && it.playAtGameTime <= upTo }

    /**
     * 步驟全部播完的時刻。
     *
     * @param steps 依序播放的步驟。
     * @return 最後一個等待或動作結束的時刻。沒有計時步驟時為負無限大。
     */
    fun endTime(steps: List<AnimationStep<TilePose>>): Double {
        var cursor = Double.NEGATIVE_INFINITY
        steps.forEach { step ->
            when (step) {
                is AnimationStep.WaitUntil -> cursor = maxOf(cursor, step.gameTime.toDouble())
                is AnimationStep.PlayMotion -> cursor = (if (cursor.isInfinite()) 0.0 else cursor) + step.durationTicks
                else -> Unit
            }
        }
        return cursor
    }

    /**
     * 動作步驟進行中某一刻的樣子，與牌實體的繪製用同一套軌跡計算。
     *
     * @param step 動作步驟。
     * @param target 動作的終點。
     * @param invisible 是否隱形。
     * @param elapsedTicks 動作開始後經過的 tick。
     * @return 這一刻的樣子。
     */
    private fun motionSample(step: AnimationStep.PlayMotion, target: MahjongTileWallPlacement, invisible: Boolean, elapsedTicks: Double): TileAnimationSample {
        val frame = TileMotionAnimation(
            spec = TileMotionAnimationSpec(
                durationTicks = step.durationTicks,
                arcHeight = step.arcHeight,
                startPoseRotationDegrees = step.startPoseRotationDegrees,
                endPoseRotationDegrees = step.endPoseRotationDegrees,
                easeRotation = step.easeRotation,
                liftCarryLower = step.liftCarryLower,
                leanDegrees = step.leanDegrees,
            ),
        ).frame(
            elapsedTicks = elapsedTicks,
            startOffset = DiceAnimationVector(step.startOffsetX, step.startOffsetY, step.startOffsetZ),
            startYawOffsetDegrees = step.startYawOffsetDegrees,
        )
        return TileAnimationSample(
            placement = MahjongTileWallPlacement(
                x = target.x + frame.offset.x,
                y = target.y + frame.offset.y,
                z = target.z + frame.offset.z,
                yaw = target.yaw + frame.yawOffsetDegrees,
            ),
            poseDegrees = frame.poseRotationDegrees,
            lean = frame.lean,
            invisible = invisible,
        )
    }
}
