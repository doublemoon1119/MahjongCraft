package com.doublemoon1119.mahjongcraft.ai.riichi

import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModel
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.ReadingDepth
import com.doublemoon1119.mahjongcraft.ai.expectation.ThreatEstimate
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.PositionRules
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/**
 * 日本麻將的對手模型，只使用公開資訊與規則查詢 [rules] 提供的規則事實。
 *
 * - 捨牌危險度：列舉對手可能持有、並以這張牌和牌的聽牌型（兩面、坎張、邊張、雙碰、單騎），依評估者看不到的
 *   牌張數量加權。對手自己打過的牌（現物）不可能榮和；對手捨過兩面另一側的牌時，這個兩面因振聽而排除，
 *   筋因此自然較安全；構成聽牌型所需的牌已全部出現時該聽牌型不成立，壁因此自然較安全。
 * - 對手威脅：立直者視為確定聽牌；其餘依副露數與捨牌數估計聽牌可能性，打點依立直、門清或副露、
 *   副露中的寶牌與是否為莊家估計。
 *
 * @property rules 本局規則的規則查詢。
 * @property readingDepth 推測對手手牌的深度。
 */
class RiichiOpponentModel(
    private val rules: PositionRules,
    override val readingDepth: ReadingDepth,
) : OpponentModel {
    override fun baselineWinValue(view: PositionView, playerId: Uuid): Int {
        val player = view.player(playerId)
        val isRiichi = (player.playerRuleState as? RiichiPlayerState)?.isRiichi == true
        val isOpen = player.hand.melds.any { it.type != MeldType.CLOSED_KAN }
        val base = when {
            isRiichi -> RIICHI_BASELINE_VALUE
            isOpen -> OPEN_BASELINE_VALUE
            else -> CLOSED_BASELINE_VALUE
        }
        val doraInMelds = player.hand.melds
            .flatMap { meld -> meld.tiles.mapNotNull { it.tile } }
            .sumOf { rules.bonusTileCount(view, it) }
        val value = base + doraInMelds * DORA_BASELINE_BONUS
        return if (view.snapshot.dealerPlayerId == playerId) (value * DEALER_BASELINE_MULTIPLIER).roundToInt() else value
    }

    override fun discardDanger(
        view: PositionView,
        opponentId: Uuid,
        tile: Tile,
    ): Double {
        val canonical = tile.riichiCanonical
        val safeTiles = rules.ronExclusions(view, opponentId).ownDiscards
        if (canonical in safeTiles) return 0.0

        val unseen = unseenCounts(view)
        val unseenOf = { candidate: Tile -> unseen[candidate] ?: 0 }
        var weight = TANKI_WEIGHT * unseenOf(canonical) / COPIES_PER_TILE +
            SHANPON_WEIGHT * pairCount(unseenOf(canonical)) / pairCount(COPIES_PER_TILE)

        if (canonical is Tile.Numeric) {
            val value = canonical.value
            val numbered = { number: Int -> Tile.Numeric(canonical.suit, number) }
            val holds = { first: Int, second: Int ->
                unseenOf(numbered(first)).toDouble() * unseenOf(numbered(second)) / (COPIES_PER_TILE * COPIES_PER_TILE)
            }
            if (value <= MAX_LOW_RYANMEN_VALUE && numbered(value + RYANMEN_SPAN) !in safeTiles) {
                weight += RYANMEN_WEIGHT * holds(value + 1, value + 2)
            }
            if (value >= MIN_HIGH_RYANMEN_VALUE && numbered(value - RYANMEN_SPAN) !in safeTiles) {
                weight += RYANMEN_WEIGHT * holds(value - 2, value - 1)
            }
            if (value in MIN_KANCHAN_VALUE..MAX_KANCHAN_VALUE) {
                weight += KANCHAN_WEIGHT * holds(value - 1, value + 1)
            }
            if (value == LOW_PENCHAN_VALUE) weight += PENCHAN_WEIGHT * holds(1, 2)
            if (value == HIGH_PENCHAN_VALUE) weight += PENCHAN_WEIGHT * holds(8, 9)
        }
        return (weight * DANGER_SCALE).coerceIn(0.0, 1.0)
    }

    override fun threat(view: PositionView, opponentId: Uuid): ThreatEstimate {
        val opponent = view.player(opponentId)
        val readyProbability = if ((opponent.playerRuleState as? RiichiPlayerState)?.isRiichi == true) {
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

    /** 每種牌對評估者而言還沒看到的張數：總數扣除自己的手牌、所有牌河、所有公開副露與牌山中公開的牌。 */
    private fun unseenCounts(view: PositionView): Map<Tile, Int> {
        val self = view.evaluator
        val visible = buildList {
            // 快照的立牌已包含剛摸到的牌
            addAll(self.hand.standingTiles.distinctBy { it.id }.mapNotNull { it.tile })
            view.snapshot.players.forEach { player ->
                player.discardPile.entries.filterNot { it.isTaken }.forEach { add(it.tile.tile) }
                player.hand.melds.forEach { meld -> addAll(meld.tiles.mapNotNull { it.tile }) }
            }
            addAll(view.snapshot.tileWall.tiles.mapNotNull { it.tile })
        }
        val seen = visible.groupingBy { it.riichiCanonical }.eachCount()
        return TILE_KINDS.associateWith { kind -> (COPIES_PER_TILE - (seen[kind] ?: 0)).coerceAtLeast(0) }
    }

    /** 從 [count] 張中挑兩張的組合數。 */
    private fun pairCount(count: Int): Double = count * (count - 1) / 2.0

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

        /** 兩面聽牌兩側牌張的差距。 */
        const val RYANMEN_SPAN = 3

        /** 以較大一側完成兩面時，這張牌的最大值。 */
        const val MAX_LOW_RYANMEN_VALUE = 6

        /** 以較小一側完成兩面時，這張牌的最小值。 */
        const val MIN_HIGH_RYANMEN_VALUE = 4

        /** 可以作為坎張聽牌的最小值。 */
        const val MIN_KANCHAN_VALUE = 2

        /** 可以作為坎張聽牌的最大值。 */
        const val MAX_KANCHAN_VALUE = 8

        /** 一二聽三的邊張。 */
        const val LOW_PENCHAN_VALUE = 3

        /** 八九聽七的邊張。 */
        const val HIGH_PENCHAN_VALUE = 7

        /** 兩面聽牌的相對出現頻率。 */
        const val RYANMEN_WEIGHT = 1.0

        /** 坎張聽牌的相對出現頻率。 */
        const val KANCHAN_WEIGHT = 0.35

        /** 邊張聽牌的相對出現頻率。 */
        const val PENCHAN_WEIGHT = 0.3

        /** 雙碰聽牌的相對出現頻率。 */
        const val SHANPON_WEIGHT = 0.45

        /** 單騎聽牌的相對出現頻率。 */
        const val TANKI_WEIGHT = 0.2

        /** 將聽牌型權重換算為放銃可能性的比例。 */
        const val DANGER_SCALE = 0.043

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

/** 登記內建日麻的對手模型。 */
fun OpponentModelRegistry.registerRiichiOpponentModel() {
    register(BuiltInRuleModuleIds.RIICHI) { module, depth -> RiichiOpponentModel(rules = module.createPositionRules(), readingDepth = depth) }
}
