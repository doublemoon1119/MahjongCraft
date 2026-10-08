package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundPhase
import com.doublemoon1119.mahjongcraft.logic.table.RankablePlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/**
 * 把點數得失換算成包含名次得失的價值。
 *
 * 啟用時，一筆得失的價值為點數本身，加上自己名次每升降一位的 [stepPoints]；名次以規則的整場排名比較，
 * 只改變自己的分數。未啟用時價值就是點數。
 *
 * @property players 目前所有玩家的排名資料。
 * @property selfIndex 自己在 [players] 中的位置。
 * @property ranking 規則的整場排名順序。
 * @property enabled 是否換算名次。
 * @property stepPoints 名次每升降一位相當的點數。
 */
internal class PlacementUtility private constructor(
    private val players: List<RankablePlayer>,
    private val selfIndex: Int,
    private val ranking: Comparator<RankablePlayer>,
    private val enabled: Boolean,
    private val stepPoints: Int,
) {
    /** 自己得到 [points] 點的價值。 */
    fun gain(points: Double): Double = if (enabled) points + placementChange(points) else points

    /** 自己失去 [points] 點的損失；恆為正數。 */
    fun loss(points: Double): Double = if (enabled) points - placementChange(-points) else points

    /** 自己分數變動 [delta] 後，名次變化換算的點數；名次上升為正。 */
    private fun placementChange(delta: Double): Double {
        val self = players[selfIndex]
        val moved = RankedSeat(
            score = self.score + delta.roundToInt(),
            seatWind = self.seatWind,
            initialSeatIndex = self.initialSeatIndex,
        )
        val placeBefore = placeOf(players, self)
        val placeAfter = placeOf(players.toMutableList().also { it[selfIndex] = moved }, moved)
        return (placeBefore - placeAfter) * stepPoints.toDouble()
    }

    /** [target] 在 [candidates] 中依規則排名的位置，第一名為 0。 */
    private fun placeOf(candidates: List<RankablePlayer>, target: RankablePlayer): Int = candidates.sortedWith(ranking).indexOf(target)

    /**
     * 排名比較用的玩家資料。
     *
     * @property score 分數。
     * @property seatWind 自風。
     * @property initialSeatIndex 起始座位。
     */
    private data class RankedSeat(
        override val score: Int,
        override val seatWind: Wind,
        override val initialSeatIndex: Int,
    ) : RankablePlayer

    /** [PlacementUtility] 的建立方式。 */
    companion object {
        /**
         * 在 [considersPlacement] 且目前為原定最後一局或延長局時啟用名次換算。
         *
         * @param ranking 規則的整場排名順序。
         * @param stepPoints 名次每升降一位相當的點數。
         */
        fun from(
            snapshot: TableStateSnapshot,
            selfId: Uuid,
            ranking: Comparator<RankablePlayer>,
            considersPlacement: Boolean,
            stepPoints: Int,
        ): PlacementUtility {
            val position = snapshot.roundPosition
            val nearEnd = position.phase == MatchRoundPhase.EXTRA ||
                position.sequenceIndex >= snapshot.config.scheduledRoundCount - 1
            val players = snapshot.players.map {
                RankedSeat(score = it.score, seatWind = it.seatWind, initialSeatIndex = it.initialSeatIndex)
            }
            return PlacementUtility(
                players = players,
                selfIndex = snapshot.players.indexOfFirst { it.id == selfId },
                ranking = ranking,
                enabled = considersPlacement && nearEnd,
                stepPoints = stepPoints,
            )
        }
    }
}
