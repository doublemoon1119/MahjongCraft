package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.PositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import kotlin.math.abs
import kotlin.uuid.Uuid

/**
 * 所有規則共用的讀牌：依對手公開的牌河、副露與規則事實，推測一張牌比平常更危險或更安全。
 *
 * 只使用「數字牌有花色與數字」這個共通概念，規則相關的事實一律向 [rules] 查詢，牌面一律先以 [interpretation] 正規化。
 * - 對手很早就打出的數字牌，往一或九那一側的同花色牌比較不會是他在等的牌；離那張牌越近，
 *   用到它的聽牌方式越多，因此越安全。
 * - 對手的副露都是同一個花色、牌河裡又幾乎沒有那個花色時，他很可能只收那個花色：
 *   那個花色比較危險、其他花色比較安全；副露有字牌時，字牌也比較危險。
 * - 對手宣告聽牌時打出的牌，旁邊兩格內的同花色牌比較危險：宣告時常常是拆掉「這張牌加上旁邊的牌」，
 *   留下旁邊的牌等牌。
 * - 會依規則額外加計打點的牌，以及它旁邊兩格內的同花色牌比較危險：對手傾向留下這些牌組成聽牌。
 *
 * @property interpretation 規則的牌面正規化。
 * @property rules 本局規則的規則查詢。
 */
class OpponentReading(
    private val interpretation: TileInterpretationPolicy,
    private val rules: PositionRules,
) {
    /** 評估者打出 [tile] 時，對 [opponentId] 的危險度相對於平常的倍率；小於 1 表示較安全。 */
    fun dangerFactor(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double {
        val opponent = view.player(opponentId)
        val canonical = interpretation.canonicalize(tile)
        return earlyOutsideFactor(opponent, canonical) *
            flushFactor(opponent, canonical) *
            declarationNeighborFactor(view, opponentId, canonical) *
            bonusNeighborFactor(view, canonical)
    }

    /** 對手很早打出的數字牌讓 [tile] 變安全的倍率；多張早打的牌取最小倍率，不連乘。 */
    internal fun earlyOutsideFactor(opponent: MahjongPlayerSnapshot, tile: Tile): Double {
        if (tile !is Tile.Numeric) return 1.0
        return opponent.discardPile.entries
            .take(EARLY_DISCARD_COUNT)
            .mapNotNull { entry -> interpretation.canonicalize(entry.tile.tile) as? Tile.Numeric }
            .filter { it.suit == tile.suit }
            .minOfOrNull { early -> outsideFactor(early = early.value, value = tile.value) }
            ?: 1.0
    }

    /** 對手只收單一花色時 [tile] 的倍率；看不出來時為 1。 */
    internal fun flushFactor(opponent: MahjongPlayerSnapshot, tile: Tile): Double {
        val signal = flushSignal(opponent) ?: return 1.0
        return when (tile) {
            is Tile.Numeric -> if (tile.suit == signal.suit) FLUSH_SUIT_FACTOR else OFF_SUIT_FACTOR
            is Tile.Honor -> if (signal.withHonors) FLUSH_SUIT_FACTOR else 1.0
            is Tile.Extension -> 1.0
        }
    }

    /** 從副露與牌河判斷對手是否只收單一花色；看不出來時為 null。 */
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

    /** [tile] 在 [opponentId] 宣告聽牌時打出的牌旁邊兩格內時的倍率；不在旁邊或對手沒有宣告時為 1。 */
    internal fun declarationNeighborFactor(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double {
        val declared = rules.declarationTile(view, opponentId) as? Tile.Numeric ?: return 1.0
        if (tile !is Tile.Numeric || tile.suit != declared.suit) return 1.0
        return if (abs(tile.value - declared.value) in 1..NEAR_DISTANCE) DECLARATION_NEIGHBOR_FACTOR else 1.0
    }

    /** [tile] 本身或旁邊兩格內的同花色牌會加計打點時的倍率；兩者都符合時取較大的倍率，不連乘。 */
    internal fun bonusNeighborFactor(view: PositionView, tile: Tile): Double {
        if (rules.bonusTileCount(view, tile) > 0) return BONUS_TILE_FACTOR
        if (tile !is Tile.Numeric) return 1.0
        val hasBonusNeighbor = (1..NEAR_DISTANCE)
            .flatMap { distance -> listOf(tile.value - distance, tile.value + distance) }
            .filter { it in MIN_NUMBER..MAX_NUMBER }
            .any { value -> rules.bonusTileCount(view, Tile.Numeric(tile.suit, value)) > 0 }
        return if (hasBonusNeighbor) BONUS_NEIGHBOR_FACTOR else 1.0
    }

    /** 牌張 [value] 相對於很早打出的 [early] 的倍率；不在往一或九那一側時為 1。 */
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
     * 對手只收單一花色的跡象。
     *
     * @property suit 對手在收的花色。
     * @property withHonors 副露是否含字牌。
     */
    internal data class FlushSignal(
        val suit: Tile.Suit,
        val withHonors: Boolean,
    )

    /** [OpponentReading] 的讀牌參數。 */
    internal companion object {
        /** 數字牌最小的數字。 */
        const val MIN_NUMBER: Int = 1

        /** 數字牌最大的數字。 */
        const val MAX_NUMBER: Int = 9

        /** 視為很早打出的捨牌張數。 */
        const val EARLY_DISCARD_COUNT: Int = 6

        /** 往一與往九兩側都算的中間數字。 */
        const val MIDDLE_VALUE: Int = 5

        /** 視為「旁邊」的最大距離。 */
        const val NEAR_DISTANCE: Int = 2

        /** 緊鄰很早打出的牌、往一或九那一側的牌的倍率。 */
        const val NEAR_OUTSIDE_FACTOR: Double = 0.7

        /** 離很早打出的牌較遠、往一或九那一側的牌的倍率。 */
        const val FAR_OUTSIDE_FACTOR: Double = 0.85

        /** 很早打出五時，緊鄰的牌的倍率。 */
        const val MIDDLE_NEAR_OUTSIDE_FACTOR: Double = 0.85

        /** 很早打出五時，較遠的牌的倍率。 */
        const val MIDDLE_FAR_OUTSIDE_FACTOR: Double = 0.95

        /** 判斷只收單一花色所需的最少捨牌數。 */
        const val MIN_FLUSH_DISCARDS: Int = 5

        /** 只收單一花色時，那個花色在牌河中所占比例的上限。 */
        const val MAX_FLUSH_SUIT_DISCARD_SHARE: Double = 0.15

        /** 對手在收的花色（以及副露含字牌時的字牌）的倍率。 */
        const val FLUSH_SUIT_FACTOR: Double = 1.5

        /** 對手不收的花色的倍率。 */
        const val OFF_SUIT_FACTOR: Double = 0.3

        /** 宣告聽牌時打出的牌旁邊兩格內的倍率。 */
        const val DECLARATION_NEIGHBOR_FACTOR: Double = 1.5

        /** 會加計打點的牌本身的倍率。 */
        const val BONUS_TILE_FACTOR: Double = 1.3

        /** 會加計打點的牌旁邊兩格內的倍率。 */
        const val BONUS_NEIGHBOR_FACTOR: Double = 1.2
    }
}
