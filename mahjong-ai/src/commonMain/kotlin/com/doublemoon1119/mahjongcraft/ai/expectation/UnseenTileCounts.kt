package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 評估者眼中每種牌還沒看到的張數。
 *
 * 牌面一律以規則的 [MahjongRuleModule.createTileInterpretationPolicy] 正規化，例如日麻的赤五與普通五算同一種。
 *
 * @property kinds 規則牌山中存在的每種正規化牌面，依規則牌序排列。
 * @property counts 每種牌面的未見張數。
 */
internal class UnseenTileCounts private constructor(
    val kinds: List<Tile>,
    private val counts: Map<Tile, Int>,
) {
    /** 所有未見牌的總張數。 */
    val total: Int = counts.values.sum()

    /** 正規化牌面 [kind] 的未見張數。 */
    operator fun get(kind: Tile): Int = counts[kind] ?: 0

    /** 假設摸進一張 [kind] 之後的未見張數。 */
    fun without(kind: Tile): UnseenTileCounts = UnseenTileCounts(
        kinds = kinds,
        counts = counts + (kind to (get(kind) - 1).coerceAtLeast(0)),
    )

    /** [UnseenTileCounts] 的建立方式。 */
    companion object {
        /**
         * 由 [selfId] 的視角計算未見張數。
         *
         * 自己的手牌與副露一律扣除；[countsVisibleTiles] 為 `true` 時再扣除所有牌河中未被鳴走的牌、他家公開的副露與
         * 牌山中已公開的牌。
         */
        fun from(
            snapshot: TableStateSnapshot,
            selfId: Uuid,
            module: MahjongRuleModule<*>,
            countsVisibleTiles: Boolean,
        ): UnseenTileCounts {
            val interpretation = module.createTileInterpretationPolicy()
            val composition = module.createWallFactory().create().getAllTiles()
                .groupingBy { interpretation.canonicalize(it.tile) }
                .eachCount()
            val self = snapshot.players.first { it.id == selfId }
            val seen = buildList {
                addAll(self.hand.standingTiles.distinctBy { it.id }.mapNotNull { it.tile })
                self.hand.melds.forEach { meld -> addAll(meld.tiles.mapNotNull { it.tile }) }
                if (countsVisibleTiles) {
                    snapshot.players.forEach { player ->
                        player.discardPile.entries.filterNot { it.isTaken }.forEach { add(it.tile.tile) }
                        if (player.id != selfId) {
                            player.hand.melds.forEach { meld -> addAll(meld.tiles.mapNotNull { it.tile }) }
                        }
                    }
                    addAll(snapshot.tileWall.tiles.mapNotNull { it.tile })
                }
            }.groupingBy { interpretation.canonicalize(it) }.eachCount()

            val kinds = composition.keys.sortedWith(module.tileOrder)
            return UnseenTileCounts(
                kinds = kinds,
                counts = kinds.associateWith { kind -> (composition.getValue(kind) - (seen[kind] ?: 0)).coerceAtLeast(0) },
            )
        }
    }
}
