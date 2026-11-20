package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongBotArmAnimation
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.HandRevealSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PlannedTileMotionSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableStepMotionPlanner
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileAnimationSample
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileAnimationSampler
import kotlin.uuid.Uuid

/**
 * 回放往後一步時牌的動畫：比較前後兩步的擺放，用對局中同一套規劃與動畫步驟播放，時間以遊戲 tick 計。
 *
 * 有牌從手上打進牌河的玩家在動畫開頭揮一次手，與遊戲中打牌時相同。這一步開始攤牌的手牌不跟著一般的牌動作，等其他牌
 * 動完後照對局中同一套倒牌步驟（[HandRevealSteps]）理牌、倒下或蓋下。
 *
 * @property roundNumber 局序號。
 * @property toStep 動畫結束時的步數。
 * @param before 這一步開始時的擺放，依局內牌索引。
 * @param after 這一步結束時的擺放，依局內牌索引。
 * @param reveals 這一步開始攤牌的手牌。
 */
internal class HistoryReplayAnimation(
    val roundNumber: Int,
    val toStep: Int,
    private val before: Map<Int, ArrangedTile>,
    after: Map<Int, ArrangedTile>,
    reveals: List<HistoryReplayHandRevealMotion> = emptyList(),
) {
    /** 有動作的牌依序播放的步驟，依局內牌索引。 */
    private val tileSteps: Map<Int, List<AnimationStep<TilePose>>>

    /** 這一步打出牌、要揮手的玩家，以回放擺牌的玩家識別碼表示。 */
    private val swingingPlayers: Set<Uuid>

    /** 整步播完需要的 tick；有人揮手時至少等揮手做完。 */
    val durationTicks: Double

    /** 已經播放的 tick。 */
    var elapsedTicks: Double = 0.0
        private set

    init {
        val revealing = reveals.flatMapTo(mutableSetOf()) { it.tiles }
        val motions = TableStepMotionPlanner.plan(
            previous = before.entries.associate { (index, tile) -> HistoryReplayArrangement.tileId(index) to tile },
            next = after.entries.associate { (index, tile) -> HistoryReplayArrangement.tileId(index) to tile },
            excludedTileIds = revealing.mapTo(mutableSetOf(), HistoryReplayArrangement::tileId),
        )
        val indexById = after.keys.associateBy(HistoryReplayArrangement::tileId)
        val motionTicks = TableStepMotionPlanner.stepDurationTicks(motions).toDouble()
        val revealStart = motionTicks.toLong()
        val revealSteps = reveals.map { it.steps(before, after, revealStart) }
        tileSteps = PlannedTileMotionSteps.forStep(motions, startGameTime = 0L).entries.associate { (id, steps) -> indexById.getValue(id) to steps } +
            revealSteps.flatMap { it.steps.entries }.associate { it.key to it.value }
        swingingPlayers = after.entries.mapNotNull { (index, tile) ->
            val from = before[index]?.row ?: return@mapNotNull null
            tile.row.ownerId.takeIf { from.region in HAND_REGIONS && tile.row.region == TileRegion.DISCARD && from.ownerId == it }
        }.toSet()
        val swingTicks = if (swingingPlayers.isEmpty()) 0.0 else MahjongBotArmAnimation.DURATION_TICKS.toDouble()
        durationTicks = maxOf(motionTicks, swingTicks, revealSteps.maxOfOrNull { it.endTick.toDouble() } ?: 0.0)
    }

    /**
     * 某一家目前的揮手進度。
     *
     * @param initialSeatIndex 玩家的初始座位。
     * @return 揮手中為 0 到 1 之間的進度；沒有揮手或已經做完時為 0。
     */
    fun swingProgress(initialSeatIndex: Int): Float {
        if (HistoryReplayArrangement.playerId(initialSeatIndex) !in swingingPlayers) return 0f
        val progress = (elapsedTicks / MahjongBotArmAnimation.DURATION_TICKS).toFloat()
        return if (progress < 1f) progress else 0f
    }

    /** 是否已經播完。 */
    val finished: Boolean
        get() = elapsedTicks >= durationTicks

    /** 這一步有動作的牌，依局內牌索引；與對局中相同，動畫播放中這些牌不參與發光。 */
    val movingTiles: Set<Int>
        get() = tileSteps.keys

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

    /** 判斷打牌所用的區域。 */
    private companion object {
        /** 打牌時牌原本所在的區域：手牌與剛摸進的牌。 */
        val HAND_REGIONS: Set<TileRegion> = setOf(TileRegion.HAND, TileRegion.DRAWN)
    }
}
