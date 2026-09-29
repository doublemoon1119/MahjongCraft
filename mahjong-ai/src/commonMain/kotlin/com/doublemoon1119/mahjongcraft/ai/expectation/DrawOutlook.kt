package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.math.pow

/**
 * 本局剩餘的和牌機會。
 *
 * 一輪指自己摸一張牌，以及在下一次摸牌前其他仍在局中的玩家各打出一張牌。
 *
 * @property ownDraws 自己剩餘的摸牌次數，也就是剩餘輪數。
 * @property ronChancesPerCycle 每一輪中，對手捨牌折算成與自己摸牌同等的和牌機會數。
 * @property continuationRate 每一輪結束時，本局沒有因其他玩家和牌而結束的機率。
 */
internal data class DrawOutlook(
    val ownDraws: Int,
    val ronChancesPerCycle: Double,
    val continuationRate: Double,
) {
    /** [DrawOutlook] 的建立方式。 */
    companion object {
        /** 以牌山快照扣除王牌後的張數，平均分給仍在局中的玩家。 */
        fun from(snapshot: TableStateSnapshot): DrawOutlook {
            val activePlayers = (snapshot.players.size - snapshot.finishedPlayerIds.size).coerceAtLeast(1)
            val liveWall = (snapshot.tileWall.tiles.size - snapshot.config.deadTileCount).coerceAtLeast(0)
            return DrawOutlook(
                ownDraws = liveWall / activePlayers,
                ronChancesPerCycle = (activePlayers - 1) * ExpectationTuning.OPPONENT_DISCARD_DISCOUNT,
                continuationRate = (1 - ExpectationTuning.ROUND_END_RATE_PER_OPPONENT).pow(activePlayers - 1),
            )
        }
    }
}
