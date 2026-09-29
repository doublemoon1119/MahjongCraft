package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import kotlin.math.abs
import kotlin.uuid.Uuid

/**
 * 不限定規則的讀牌：依對手公開的牌河與副露，推測一張牌相對於平常的危險度。
 *
 * 只使用「數字牌有花色與數字」這個共通概念，牌面一律先以 [interpretation] 正規化。
 * - 早外：對手早巡打出的數字牌，其外側（靠近端點一側）的同花色牌較不可能是他的聽牌；
 *   離早打的牌越近，受影響的聽牌型越多，折扣越強。
 * - 染手訊號：對手的副露都是同一花色、牌河幾乎沒有那個花色時，那個花色較危險、其他花色較安全；
 *   副露含字牌時字牌也較危險。
 *
 * @property interpretation 規則的牌面正規化。
 */
class OpponentReading(private val interpretation: TileInterpretationPolicy) {
    /** 評估者打出 [tile] 時，對 [opponentId] 的危險度相對於平常的倍率；小於 1 表示較安全。 */
    fun dangerFactor(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double {
        val opponent = view.player(opponentId)
        val canonical = interpretation.canonicalize(tile)
        return earlyOutsideFactor(opponent, canonical) * flushFactor(opponent, canonical)
    }

    /** 早外的倍率；多張早打的牌取最小倍率，不連乘。 */
    internal fun earlyOutsideFactor(opponent: MahjongPlayerSnapshot, tile: Tile): Double {
        if (tile !is Tile.Numeric) return 1.0
        return opponent.discardPile.entries
            .take(EARLY_DISCARD_COUNT)
            .mapNotNull { entry -> interpretation.canonicalize(entry.tile.tile) as? Tile.Numeric }
            .filter { it.suit == tile.suit }
            .minOfOrNull { early -> outsideFactor(early = early.value, value = tile.value) }
            ?: 1.0
    }

    /** 染手訊號的倍率；沒有訊號時為 1。 */
    internal fun flushFactor(opponent: MahjongPlayerSnapshot, tile: Tile): Double {
        val signal = flushSignal(opponent) ?: return 1.0
        return when (tile) {
            is Tile.Numeric -> if (tile.suit == signal.suit) FLUSH_SUIT_FACTOR else OFF_SUIT_FACTOR
            is Tile.Honor -> if (signal.withHonors) FLUSH_SUIT_FACTOR else 1.0
            is Tile.Extension -> 1.0
        }
    }

    /** 從副露與牌河判斷對手是否在做同一花色的手；不成立時為 null。 */
    internal fun flushSignal(opponent: MahjongPlayerSnapshot): FlushSignal? {
        val melds = opponent.hand.melds
        if (melds.none { it.type != MeldType.CLOSED_KAN }) return null
        val meldTiles = melds.flatMap { meld -> meld.tiles.mapNotNull { it.tile?.let(interpretation::canonicalize) } }
        val suit = meldTiles.filterIsInstance<Tile.Numeric>().map { it.suit }.distinct().singleOrNull() ?: return null
        val discards = opponent.discardPile.entries.map { entry -> interpretation.canonicalize(entry.tile.tile) }
        if (discards.size < MIN_FLUSH_DISCARDS) return null
        val suitDiscards = discards.count { it is Tile.Numeric && it.suit == suit }
        if (suitDiscards > discards.size * MAX_FLUSH_SUIT_DISCARD_SHARE) return null
        return FlushSignal(suit = suit, withHonors = meldTiles.any { it is Tile.Honor })
    }

    /** 牌張 [value] 相對於早打的 [early] 的倍率；不在外側時為 1。 */
    private fun outsideFactor(early: Int, value: Int): Double {
        val isOutside = when {
            value == early -> false
            early < MIDDLE_VALUE -> value < early
            early > MIDDLE_VALUE -> value > early
            else -> true
        }
        if (!isOutside) return 1.0
        val isNear = abs(value - early) <= NEAR_DISTANCE
        return when {
            early == MIDDLE_VALUE && isNear -> MIDDLE_NEAR_OUTSIDE_FACTOR
            early == MIDDLE_VALUE -> MIDDLE_FAR_OUTSIDE_FACTOR
            isNear -> NEAR_OUTSIDE_FACTOR
            else -> FAR_OUTSIDE_FACTOR
        }
    }

    /**
     * 染手訊號。
     *
     * @property suit 對手在做的花色。
     * @property withHonors 副露是否含字牌。
     */
    internal data class FlushSignal(
        val suit: Tile.Suit,
        val withHonors: Boolean,
    )

    /** [OpponentReading] 的讀牌參數。 */
    internal companion object {
        /** 視為早巡的捨牌張數。 */
        const val EARLY_DISCARD_COUNT: Int = 6

        /** 兩側都算外側的中張。 */
        const val MIDDLE_VALUE: Int = 5

        /** 視為緊鄰早打的牌的最大距離。 */
        const val NEAR_DISTANCE: Int = 2

        /** 緊鄰早打的牌的外側倍率。 */
        const val NEAR_OUTSIDE_FACTOR: Double = 0.7

        /** 離早打的牌較遠的外側倍率。 */
        const val FAR_OUTSIDE_FACTOR: Double = 0.85

        /** 早打五時，緊鄰的外側倍率。 */
        const val MIDDLE_NEAR_OUTSIDE_FACTOR: Double = 0.85

        /** 早打五時，較遠的外側倍率。 */
        const val MIDDLE_FAR_OUTSIDE_FACTOR: Double = 0.95

        /** 判斷染手訊號所需的最少捨牌數。 */
        const val MIN_FLUSH_DISCARDS: Int = 5

        /** 染手花色在牌河中所占比例的上限。 */
        const val MAX_FLUSH_SUIT_DISCARD_SHARE: Double = 0.15

        /** 染手花色（以及副露含字牌時的字牌）的倍率。 */
        const val FLUSH_SUIT_FACTOR: Double = 1.5

        /** 染手以外花色的倍率。 */
        const val OFF_SUIT_FACTOR: Double = 0.3
    }
}
