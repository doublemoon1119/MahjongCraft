package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.PositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.tile.IdentityTileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import kotlin.uuid.Uuid

/**
 * 從公開資訊估計的對手威脅。
 *
 * @property readyProbability 對手已經聽牌的可能性，介於 0 到 1。
 * @property expectedWinValue 對手和牌時預估可得的點數；放銃給他時的損失以此估計。
 */
data class ThreatEstimate(
    val readyProbability: Double,
    val expectedWinValue: Int,
) {
    init {
        require(readyProbability in 0.0..1.0) { "Ready probability must be between 0 and 1" }
        require(expectedWinValue >= 0) { "Expected win value must not be negative" }
    }
}

/**
 * AI 對一個局面中各玩家的估計：預估打點、捨牌危險度與聽牌可能性。
 *
 * 這些都是估計而不是規則事實；需要規則事實（例如不能榮和的牌、寶牌）時，實作從規則模組的 [PositionRules] 取得。
 * 所有方法只讀取 [PositionView]，不可能使用評估者看不到的資訊。
 */
interface OpponentModel {
    /** 推測對手手牌的深度。 */
    val readingDepth: ReadingDepth

    /** [playerId] 一手和牌的預估基準點數，用於無法具體估算手牌價值時；[playerId] 可以是評估者本人。 */
    fun baselineWinValue(view: PositionView, playerId: Uuid): Int

    /** 評估者打出 [tile] 時，被已經聽牌的 [opponentId] 榮和的可能性，介於 0 到 1。 */
    fun discardDanger(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double

    /** 從公開資訊估計 [opponentId] 的威脅。 */
    fun threat(view: PositionView, opponentId: Uuid): ThreatEstimate
}

/**
 * 不具任何規則知識的對手模型。
 *
 * 所有和牌與放銃都以相同的單位點數計算；對手的聽牌可能性只依副露數與捨牌數粗估。捨牌危險度在基本深度不區分牌張，
 * 進階深度另外套用 [OpponentReading] 的倍率，但限制在 [MIN_READING_FACTOR] 到 [MAX_READING_FACTOR] 之間，
 * 因為不知道規則時無法確認讀牌的準確度。沒有登記專屬對手模型的規則因此仍能讓使用評估的決策正常運作，只是判斷較為保守。
 *
 * @property readingDepth 推測對手手牌的深度。
 * @param interpretation 規則的牌面正規化，供讀牌比較牌面。
 */
class NeutralOpponentModel(
    override val readingDepth: ReadingDepth,
    interpretation: TileInterpretationPolicy = IdentityTileInterpretationPolicy,
) : OpponentModel {
    /** 進階深度使用的讀牌。 */
    private val reading = OpponentReading(interpretation)

    override fun baselineWinValue(view: PositionView, playerId: Uuid): Int = UNIT_WIN_VALUE

    override fun discardDanger(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double = when (readingDepth) {
        ReadingDepth.BASIC -> UNIFORM_DISCARD_DANGER
        ReadingDepth.ADVANCED ->
            UNIFORM_DISCARD_DANGER *
                reading.dangerFactor(view, opponentId, tile).coerceIn(MIN_READING_FACTOR, MAX_READING_FACTOR)
    }

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

        /** 進階深度的讀牌倍率下限。 */
        const val MIN_READING_FACTOR: Double = 0.5

        /** 進階深度的讀牌倍率上限。 */
        const val MAX_READING_FACTOR: Double = 1.5

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
