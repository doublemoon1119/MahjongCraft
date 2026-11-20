package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PlannedTileMotionSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableStepMotionPlanner
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileAnimationSample
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileAnimationSampler

/**
 * 回放往後一步時牌的動畫：比較前後兩步的擺放，用對局中同一套規劃與動畫步驟播放，時間以遊戲 tick 計。
 *
 * @property roundNumber 局序號。
 * @property toStep 動畫結束時的步數。
 * @param before 這一步開始時的擺放，依局內牌索引。
 * @param after 這一步結束時的擺放，依局內牌索引。
 */
internal class HistoryReplayAnimation(
    val roundNumber: Int,
    val toStep: Int,
    private val before: Map<Int, ArrangedTile>,
    after: Map<Int, ArrangedTile>,
) {
    /** 有動作的牌依序播放的步驟，依局內牌索引。 */
    private val tileSteps: Map<Int, List<AnimationStep<TilePose>>>

    /** 整步播完需要的 tick。 */
    val durationTicks: Double

    /** 已經播放的 tick。 */
    var elapsedTicks: Double = 0.0
        private set

    init {
        val motions = TableStepMotionPlanner.plan(
            previous = before.entries.associate { (index, tile) -> HistoryReplayArrangement.tileId(index) to tile },
            next = after.entries.associate { (index, tile) -> HistoryReplayArrangement.tileId(index) to tile },
        )
        val indexById = after.keys.associateBy(HistoryReplayArrangement::tileId)
        tileSteps = PlannedTileMotionSteps.forStep(motions, startGameTime = 0L).entries.associate { (id, steps) -> indexById.getValue(id) to steps }
        durationTicks = TableStepMotionPlanner.stepDurationTicks(motions).toDouble()
    }

    /** 是否已經播完。 */
    val finished: Boolean
        get() = elapsedTicks >= durationTicks

    /**
     * 往前播放一段時間。
     *
     * @param ticks 播放的 tick，可以有小數。
     * @return 這段時間內到了播放時刻的聲音。
     */
    fun advance(ticks: Double): List<AnimationStep.PlaySound> {
        val from = elapsedTicks
        elapsedTicks = (elapsedTicks + ticks).coerceAtMost(durationTicks)
        return tileSteps.values.flatMap { TileAnimationSampler.sounds(it, after = from, upTo = elapsedTicks) }
    }

    /**
     * 有動作的牌在目前這一刻的樣子。
     *
     * @return 依局內牌索引的樣子；沒有動作的牌不列入，維持結束時的擺放。
     */
    fun samples(): Map<Int, TileAnimationSample> = tileSteps.mapValues { (index, steps) ->
        val start = before[index]
        TileAnimationSampler.sample(
            steps = steps,
            start = start?.placement ?: steps.firstNotNullOf { (it as? AnimationStep.Teleport)?.placement() },
            startPose = start?.pose ?: TilePose.UPRIGHT,
            time = elapsedTicks,
        )
    }

    /**
     * 瞬移步驟的位置。
     *
     * @return 位置與朝向。
     */
    private fun AnimationStep.Teleport.placement(): MahjongTileWallPlacement = MahjongTileWallPlacement(x, y, z, yaw)
}
