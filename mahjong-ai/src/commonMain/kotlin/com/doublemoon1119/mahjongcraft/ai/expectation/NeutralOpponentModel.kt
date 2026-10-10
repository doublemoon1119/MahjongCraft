package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import kotlin.uuid.Uuid

/**
 * 不具任何規則知識的對手模型。
 *
 * 所有和牌與放銃都以相同的單位點數計算、捨牌危險度不區分牌張；對手的聽牌可能性只依副露數與捨牌數粗估。
 * 沒有登記專屬對手模型的規則因此仍能讓使用評估的決策正常運作，只是判斷較為保守。
 *
 * @property readingDepth 推測對手手牌的深度。
 */
class NeutralOpponentModel(override val readingDepth: ReadingDepth) : OpponentModel {
    override fun baselineWinValue(view: PositionView, playerId: Uuid): Int = UNIT_WIN_VALUE

    override fun discardDanger(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double = UNIFORM_DISCARD_DANGER

    override fun threat(view: PositionView, opponentId: Uuid): ThreatEstimate {
        val opponent = view.player(opponentId)
        val openMelds = opponent.hand.melds.count { it.type != MeldType.CLOSED_KAN }
        val readyProbability = BASE_READY_PROBABILITY +
            READY_PROBABILITY_PER_DISCARD * opponent.discardPile.entries.size +
            READY_PROBABILITY_PER_MELD * openMelds
        return ThreatEstimate(
            readyProbability = readyProbability.coerceAtMost(MAX_READY_PROBABILITY),
            expectedWinValue = UNIT_WIN_VALUE,
        )
    }

    /** [NeutralOpponentModel] 的估計參數。 */
    companion object {
        /** 所有和牌與放銃共用的單位點數。 */
        const val UNIT_WIN_VALUE: Int = 1

        /** 不區分牌張時使用的捨牌危險度。 */
        const val UNIFORM_DISCARD_DANGER: Double = 0.1

        /** 沒有副露也沒有捨牌時的聽牌可能性。 */
        private const val BASE_READY_PROBABILITY: Double = 0.05

        /** 每一張捨牌增加的聽牌可能性。 */
        private const val READY_PROBABILITY_PER_DISCARD: Double = 0.03

        /** 每一組副露增加的聽牌可能性。 */
        private const val READY_PROBABILITY_PER_MELD: Double = 0.15

        /** 粗估時聽牌可能性的上限。 */
        private const val MAX_READY_PROBABILITY: Double = 0.9
    }
}
