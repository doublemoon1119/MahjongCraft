package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.MahjongAnimationSounds
import kotlin.uuid.Uuid

/**
 * 把一個規劃好的牌動作展開成依序播放的動畫步驟。對局中的牌實體與牌譜回放的場景播放同一份步驟。
 *
 * - 摸牌：抬起後隱形移到新位置再落下。
 * - 拋物線與弧線：一段連續可見的飛行。
 * - 平移：沒有弧線。
 * - 翻面：只轉姿態。
 * - 落地聲依目的區域：牌河為捨牌聲，副露與移出區為副露聲，從牌牆摸到立牌或摸牌位為摸牌聲。
 * - 起點使用規劃的位置與姿態，不讀牌目前的位置。前面還有動畫在排隊時，也會從那些動畫結束的位置接著動。
 */
object PlannedTileMotionSteps {
    /**
     * 展開同一步的所有動作：先離開、再讓位、最後從牌牆摸進來，後一段等前一段完成。一起落到同一排的牌只播一次落地聲。
     *
     * @param motions 同一步規劃好的動作。
     * @param startGameTime 這一步開始的絕對遊戲 tick。
     * @return 每張牌依序播放的步驟。
     */
    fun forStep(motions: List<PlannedTileMotion>, startGameTime: Long): Map<Uuid, List<AnimationStep<TilePose>>> {
        val soundTileIds = motions.groupBy { it.kind to it.toRow }.values.mapTo(mutableSetOf()) { group -> group.first().tileId }
        val phaseStarts = TableStepMotionPlanner.phaseStartTicks(motions)
        return motions.associate { motion ->
            val motionStart = startGameTime + phaseStarts.getValue(TableStepMotionPlanner.phaseOf(motion.kind))
            motion.tileId to steps(motion, motionStart, playLandingSound = motion.tileId in soundTileIds)
        }
    }

    /**
     * 展開一個動作。
     *
     * @param motion 規劃好的動作。
     * @param startGameTime 動作開始的絕對遊戲 tick。
     * @param playLandingSound 是否播放落地聲。同一組一起落地的牌只需要其中一張播放。
     * @return 依序播放的步驟。自訂步驟是牌的姿態。
     */
    fun steps(motion: PlannedTileMotion, startGameTime: Long, playLandingSound: Boolean = true): List<AnimationStep<TilePose>> {
        val to = motion.to
        val toPose = motion.toPose
        val from = motion.from
        val fromPose = motion.fromPose ?: toPose
        val steps = mutableListOf<AnimationStep<TilePose>>(AnimationStep.WaitUntil(startGameTime))
        val endGameTime = startGameTime + TableStepMotionPlanner.durationTicks(motion.kind)
        when (motion.kind) {
            TileMotionKind.PLACE, TileMotionKind.DROP -> {
                steps += AnimationStep.Teleport(to.x, to.y, to.z, to.yaw)
                steps += AnimationStep.Custom(toPose)
                if (motion.kind == TileMotionKind.DROP) {
                    steps += AnimationStep.SetInvisible(false)
                    steps += dropMotion(MahjongTileTableLayout.DRAW_LIFT_HEIGHT, toPose)
                }
            }
            TileMotionKind.DRAW -> {
                val origin = checkNotNull(from)
                val peakY = origin.y + MahjongTileTableLayout.DRAW_LIFT_HEIGHT
                steps += AnimationStep.Teleport(origin.x, peakY, origin.z, origin.yaw)
                steps += AnimationStep.Custom(fromPose)
                steps += AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = origin.y - peakY,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = fromPose.rotationDegrees,
                    endPoseRotationDegrees = fromPose.rotationDegrees,
                )
                steps += AnimationStep.SetInvisible(true)
                steps += AnimationStep.Teleport(to.x, to.y, to.z, to.yaw)
                steps += AnimationStep.Custom(toPose)
                steps += AnimationStep.WaitUntil(
                    gameTime = startGameTime + MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS + MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS,
                )
                steps += AnimationStep.SetInvisible(false)
                steps += dropMotion(peakY - to.y, toPose)
            }
            TileMotionKind.FLIGHT, TileMotionKind.SLIDE, TileMotionKind.ARC -> {
                val origin = checkNotNull(from)
                steps += AnimationStep.Teleport(to.x, to.y, to.z, to.yaw)
                steps += AnimationStep.Custom(toPose)
                steps += AnimationStep.PlayMotion(
                    durationTicks = TableStepMotionPlanner.durationTicks(motion.kind),
                    arcHeight = when (motion.kind) {
                        TileMotionKind.FLIGHT -> MahjongTileTableLayout.DISCARD_ARC_HEIGHT
                        TileMotionKind.ARC -> MahjongTileTableLayout.ROW_ARC_HEIGHT
                        else -> 0.0
                    },
                    startOffsetX = origin.x - to.x,
                    startOffsetY = origin.y - to.y,
                    startOffsetZ = origin.z - to.z,
                    startPoseRotationDegrees = fromPose.rotationDegrees,
                    endPoseRotationDegrees = toPose.rotationDegrees,
                    liftCarryLower = motion.kind == TileMotionKind.ARC,
                    leanDegrees = if (motion.kind == TileMotionKind.ARC && toPose == TilePose.UPRIGHT) {
                        MahjongTileTableLayout.ROW_ARC_LEAN_DEGREES
                    } else {
                        0.0f
                    },
                )
            }
            TileMotionKind.TURN -> {
                steps += AnimationStep.Custom(toPose)
                steps += AnimationStep.PlayMotion(
                    durationTicks = TableStepMotionPlanner.durationTicks(motion.kind),
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = 0.0,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = fromPose.rotationDegrees,
                    endPoseRotationDegrees = toPose.rotationDegrees,
                )
            }
        }
        if (playLandingSound) landingSound(motion, endGameTime)?.let(steps::add)
        return steps
    }

    /**
     * 依動作與目的區域選擇落地聲。同一排內的平移、弧線、翻面與直接擺放沒有落地聲。
     *
     * @param motion 規劃好的動作。
     * @param gameTime 落地的絕對遊戲 tick。
     * @return 落地聲。不需要時為 null。
     */
    private fun landingSound(motion: PlannedTileMotion, gameTime: Long): AnimationStep.PlaySound? = when (motion.kind) {
        TileMotionKind.DRAW, TileMotionKind.DROP -> sound(MahjongAnimationSounds.DRAW_TILE_LAND, DRAW_LAND_VOLUME, DRAW_LAND_PITCH, gameTime)
        TileMotionKind.FLIGHT -> if (motion.toRow.region == TileRegion.DISCARD) {
            sound(MahjongAnimationSounds.TILE_DISCARD_LAND, DISCARD_LAND_VOLUME, 1.0f, gameTime)
        } else {
            sound(MahjongAnimationSounds.TILE_MELD_LAND, MELD_LAND_VOLUME, 1.0f, gameTime)
        }
        TileMotionKind.PLACE, TileMotionKind.SLIDE, TileMotionKind.ARC, TileMotionKind.TURN -> null
    }

    /**
     * 建立只在預定時刻附近有效的聲音步驟。
     *
     * @param soundId 聲音 ID。
     * @param volume 音量。
     * @param pitch 音高。
     * @param gameTime 預定播放的絕對遊戲 tick。
     * @return 聲音步驟。
     */
    private fun sound(soundId: String, volume: Float, pitch: Float, gameTime: Long): AnimationStep.PlaySound = AnimationStep.PlaySound(
        soundId = soundId,
        volume = volume,
        pitch = pitch,
        playAtGameTime = gameTime,
        expiresAtGameTime = gameTime + MahjongAnimationSounds.EVENT_GRACE_TICKS,
    )

    /**
     * 從目的地正上方落下，在既定桌面格位結束。
     *
     * @param height 落下的高度。
     * @param pose 落下時的姿態。
     * @return 落下的動作步驟。
     */
    private fun dropMotion(height: Double, pose: TilePose): AnimationStep.PlayMotion = AnimationStep.PlayMotion(
        durationTicks = MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS,
        arcHeight = 0.0,
        startOffsetX = 0.0,
        startOffsetY = height,
        startOffsetZ = 0.0,
        startPoseRotationDegrees = pose.rotationDegrees,
        endPoseRotationDegrees = pose.rotationDegrees,
    )

    /** 摸牌落到摸牌位的音量。 */
    private const val DRAW_LAND_VOLUME = 0.35f

    /** 摸牌落到摸牌位的音高。 */
    private const val DRAW_LAND_PITCH = 1.1f

    /** 牌落到牌河的音量。 */
    private const val DISCARD_LAND_VOLUME = 1.05f

    /** 牌落到副露區或移出區的音量。 */
    private const val MELD_LAND_VOLUME = 1.1f
}

/**
 * 姿態在牌模型局部 X 軸上的旋轉角度：直立為 0、牌面朝上為 90、牌面朝下為 -90。
 */
val TilePose.rotationDegrees: Float
    get() = when (this) {
        TilePose.UPRIGHT -> 0.0f
        TilePose.FACE_UP -> 90.0f
        TilePose.FACE_DOWN -> -90.0f
    }
