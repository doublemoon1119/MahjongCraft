package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HandReveal
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.HandRevealSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout

/**
 * 回放某一步開始攤牌的一家手牌。
 *
 * @property tiles 這一家的手牌，依局內牌索引，立牌在前、剛摸進的牌在最後。
 * @property reveal 攤牌方式。
 */
internal data class HistoryReplayHandRevealMotion(
    val tiles: List<Int>,
    val reveal: HandReveal,
) {
    /**
     * 照對局中同一套倒牌步驟展開每張牌的動作。
     *
     * @param before 這一步開始時的擺放。
     * @param after 這一步結束時的擺放，倒下或蓋下後的位置與姿態。
     * @param startTick 攤牌開始的 tick，例如這一步其他牌動完的時刻。
     * @return 每張牌的動作與全部做完的 tick。
     */
    fun steps(before: Map<Int, ArrangedTile>, after: Map<Int, ArrangedTile>, startTick: Long): RevealSteps {
        val present = tiles.filter { it in before && it in after }
        fun startPose(index: Int): TilePose = before.getValue(index).pose
        val steps = mutableMapOf<Int, List<AnimationStep<TilePose>>>()
        val end = when (val reveal = reveal) {
            is HandReveal.Win -> {
                val timings = HandRevealSteps.winTimings(isTsumo = reveal.winningTile != null)
                val standing = present.filter { it != reveal.winningTile }
                val soundIndex = HandRevealSteps.groupSoundIndex(standing.map(::startPose))
                standing.forEachIndexed { order, index ->
                    steps[index] = HandRevealSteps.reorder(after.getValue(index).placement, startTick + timings.reorder) +
                        HandRevealSteps.laydown(startPose(index), startTick + timings.handLaydown, playSound = order == soundIndex)
                }
                val winningTile = reveal.winningTile?.takeIf { it in present }
                val winningLaydown = timings.winningTileLaydown
                if (winningTile != null && winningLaydown != null) {
                    steps[winningTile] = HandRevealSteps.reorder(after.getValue(winningTile).placement, startTick + timings.reorder) +
                        HandRevealSteps.laydown(startPose(winningTile), startTick + winningLaydown, playSound = true)
                }
                startTick + timings.end
            }
            HandReveal.DrawRevealed -> {
                val soundIndex = HandRevealSteps.groupSoundIndex(present.map(::startPose))
                present.forEachIndexed { order, index ->
                    steps[index] = HandRevealSteps.reorder(after.getValue(index).placement, startTick + HandRevealSteps.DRAW_REORDER_TICK) +
                        HandRevealSteps.laydown(startPose(index), startTick + HandRevealSteps.DRAW_REVEAL_TICK, playSound = order == soundIndex)
                }
                startTick + HandRevealSteps.DRAW_REVEAL_TICK + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
            }
            HandReveal.DrawConcealed -> {
                present.forEachIndexed { order, index ->
                    steps[index] = HandRevealSteps.conceal(startPose(index), startTick + HandRevealSteps.DRAW_REVEAL_TICK, playSound = order == present.size / 2)
                }
                startTick + HandRevealSteps.DRAW_REVEAL_TICK + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
            }
        }
        return RevealSteps(steps.filterValues { it.isNotEmpty() }, end)
    }

    /**
     * 展開後的攤牌動作。
     *
     * @property steps 每張牌的動作，依局內牌索引。
     * @property endTick 全部做完的 tick。
     */
    data class RevealSteps(val steps: Map<Int, List<AnimationStep<TilePose>>>, val endTick: Long)
}
