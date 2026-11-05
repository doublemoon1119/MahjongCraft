package com.doublemoon1119.mahjongcraft.ai.riichi

import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModel
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.ReadingDepth
import com.doublemoon1119.mahjongcraft.ai.expectation.ThreatEstimate
import com.doublemoon1119.mahjongcraft.ai.expectation.WaitShapeDanger
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.PositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileInterpretationPolicy
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/**
 * 日本麻將的對手模型，只使用公開資訊與規則查詢 [rules] 提供的規則事實。
 *
 * - 捨牌危險度：依聽牌型估計（[WaitShapeDanger]）。對手自己打過的牌不可能讓他榮和；兩面聽牌的另一側
 *   若是他打過的牌，他也不能胡，因此這種聽牌不列入。
 * - 對手威脅：立直者視為確定聽牌；其餘依副露數與捨牌數估計聽牌可能性，打點依立直、門清或副露、
 *   副露中的寶牌與是否為莊家估計。
 *
 * 所有規則共用的讀牌由 [OpponentModelRegistry] 在進階深度時另外套用，不在這裡處理。
 *
 * @property rules 本局規則的規則查詢。
 * @property readingDepth 推測對手手牌的深度。
 */
class RiichiOpponentModel(
    private val rules: PositionRules,
    override val readingDepth: ReadingDepth,
) : OpponentModel {
    private val waitShapeDanger = WaitShapeDanger(tileKinds = TILE_KINDS, copiesPerTile = COPIES_PER_TILE)

    override fun baselineWinValue(view: PositionView, playerId: Uuid): Int {
        val player = view.player(playerId)
        val isRiichi = player.riichiDeclaration != null
        val isOpen = player.hand.melds.any { it.type != MeldType.CLOSED_KAN }
        val base = when {
            isRiichi -> RIICHI_BASELINE_VALUE
            isOpen -> OPEN_BASELINE_VALUE
            else -> CLOSED_BASELINE_VALUE
        }
        val doraInMelds = player.hand.melds
            .flatMap { meld -> meld.tiles.mapNotNull { it.face?.let(RiichiTileInterpretationPolicy::valueOf) } }
            .sumOf { rules.bonusTileCount(view, it) }
        val value = base + doraInMelds * DORA_BASELINE_BONUS
        return if (view.snapshot.dealerPlayerId == playerId) (value * DEALER_BASELINE_MULTIPLIER).roundToInt() else value
    }

    override fun discardDanger(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double {
        val safeTiles = rules.ronExclusions(view, opponentId).ownDiscards
        if (tile in safeTiles) return 0.0

        return waitShapeDanger.estimate(view, tile, unwinnableTiles = safeTiles)
    }

    override fun threat(view: PositionView, opponentId: Uuid): ThreatEstimate {
        val opponent = view.player(opponentId)
        val readyProbability = if (opponent.riichiDeclaration != null) {
            1.0
        } else {
            val openMelds = opponent.hand.melds.count { it.type != MeldType.CLOSED_KAN }
            val profile = READINESS_BY_OPEN_MELDS[openMelds.coerceAtMost(READINESS_BY_OPEN_MELDS.lastIndex)]
            (profile.base + profile.perDiscard * opponent.discardPile.entries.size).coerceAtMost(profile.cap)
        }
        return ThreatEstimate(
            readyProbability = readyProbability,
            expectedWinValue = baselineWinValue(view, opponentId),
        )
    }

    /**
     * 依副露數估計聽牌可能性的參數。
     *
     * @property base 沒有捨牌時的聽牌可能性。
     * @property perDiscard 每一張捨牌增加的聽牌可能性。
     * @property cap 聽牌可能性的上限。
     */
    private data class ReadinessProfile(
        val base: Double,
        val perDiscard: Double,
        val cap: Double,
    )

    /** [RiichiOpponentModel] 的估計參數。 */
    private companion object {
        /** 每種牌的張數。 */
        const val COPIES_PER_TILE = 4

        /** 子家立直的預估打點。 */
        const val RIICHI_BASELINE_VALUE = 5200

        /** 子家門清未立直的預估打點。 */
        const val CLOSED_BASELINE_VALUE = 3900

        /** 子家副露的預估打點。 */
        const val OPEN_BASELINE_VALUE = 2000

        /** 副露中每張寶牌增加的預估打點。 */
        const val DORA_BASELINE_BONUS = 1300

        /** 莊家打點相對子家的倍率。 */
        const val DEALER_BASELINE_MULTIPLIER = 1.5

        /** 依公開副露數（0、1、2、3 組以上）估計聽牌可能性的參數。 */
        val READINESS_BY_OPEN_MELDS: List<ReadinessProfile> = listOf(
            ReadinessProfile(base = 0.02, perDiscard = 0.02, cap = 0.4),
            ReadinessProfile(base = 0.1, perDiscard = 0.03, cap = 0.6),
            ReadinessProfile(base = 0.3, perDiscard = 0.035, cap = 0.8),
            ReadinessProfile(base = 0.6, perDiscard = 0.03, cap = 0.9),
        )

        /** 日麻的 34 種牌。 */
        val TILE_KINDS: List<Tile> = Tile.Suit.entries.flatMap { suit -> (1..9).map { Tile.Numeric(suit, it) } } +
            listOf(
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.North,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Red,
            )
    }
}
