package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.PositionView

/**
 * 依聽牌型估計一張牌被已聽牌對手榮和的可能性，供使用一般數牌與字牌的規則共用。
 *
 * 列舉對手可能用這張牌胡的每一種聽牌方式（兩面、坎張、邊張、雙碰、單騎），依評估者看不到的牌張數量加權。
 * 組成某種聽牌所需的牌已經全部看得到時，這種聽牌不列入；兩面聽牌的另一側是對手不能榮和的牌時，
 * 他也不能以這種聽牌胡，因此也不列入。
 *
 * @property tileKinds 規則使用、會被打出的所有牌種。
 * @property copiesPerTile 每種牌的張數。
 */
internal class WaitShapeDanger(
    private val tileKinds: List<Tile>,
    private val copiesPerTile: Int,
) {
    /**
     * 估計評估者打出 [tile] 時被已聽牌對手榮和的可能性。
     *
     * @param view 評估者可見資訊所構成的局面視角。
     * @param tile 評估者考慮打出的牌。
     * @param unwinnableTiles 對手依規則不能榮和的牌；呼叫端已排除 [tile] 本身在其中的情況。
     * @return 介於 0 到 1 的放銃可能性。
     */
    fun estimate(
        view: PositionView,
        tile: Tile,
        unwinnableTiles: Set<Tile>,
    ): Double {
        val unseen = unseenCounts(view)
        val unseenOf = { candidate: Tile -> unseen[candidate] ?: 0 }
        var weight = TANKI_WEIGHT * unseenOf(tile) / copiesPerTile +
            SHANPON_WEIGHT * pairCount(unseenOf(tile)) / pairCount(copiesPerTile)

        if (tile is Tile.Numeric) {
            val value = tile.value
            val numbered = { number: Int -> Tile.Numeric(tile.suit, number) }
            val holds = { first: Int, second: Int ->
                unseenOf(numbered(first)).toDouble() * unseenOf(numbered(second)) / (copiesPerTile * copiesPerTile)
            }
            if (value <= MAX_LOW_RYANMEN_VALUE && numbered(value + RYANMEN_SPAN) !in unwinnableTiles) {
                weight += RYANMEN_WEIGHT * holds(value + 1, value + 2)
            }
            if (value >= MIN_HIGH_RYANMEN_VALUE && numbered(value - RYANMEN_SPAN) !in unwinnableTiles) {
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

    /**
     * 每種牌對評估者而言還沒看到的張數：總數扣除自己的手牌、所有牌河、所有公開副露與牌山中公開的牌。
     *
     * @param view 評估者可見資訊所構成的局面視角。
     * @return 各牌種剩餘未見張數。
     */
    private fun unseenCounts(view: PositionView): Map<Tile, Int> {
        val self = view.evaluator
        val visible = buildList {
            // 快照的立牌已包含剛摸到的牌
            addAll(self.hand.standingTiles.distinctBy { it.id }.mapNotNull { it.tile })
            view.snapshot.players.forEach { player ->
                player.discardPile.entries.filterNot { it.isTaken }.forEach { entry -> entry.tile.tile?.let { add(it) } }
                player.hand.melds.forEach { meld -> addAll(meld.tiles.mapNotNull { it.tile }) }
            }
            addAll(view.snapshot.tileWall.tiles.mapNotNull { it.tile })
        }
        val seen = visible.groupingBy { it }.eachCount()
        return tileKinds.associateWith { kind -> (copiesPerTile - (seen[kind] ?: 0)).coerceAtLeast(0) }
    }

    /**
     * 從 [count] 張中挑兩張的組合數。
     *
     * @param count 可供挑選的牌張數。
     * @return 不計順序的兩張組合數。
     */
    private fun pairCount(count: Int): Double = count * (count - 1) / 2.0

    /** [WaitShapeDanger] 的估計參數。 */
    private companion object {
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
    }
}
