package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * 評估者眼中每種牌還沒看到的張數。
 *
 * 牌面一律以規則的 [MahjongRuleModule.createTileInterpretationPolicy] 正規化，例如日麻的赤五與普通五算同一種。
 * 同一種牌若有外觀不同、價值也可能不同的牌面（例如日麻的赤五與普通五），另外記錄每一種牌面的未見張數，
 * 讓和牌價值可以依實際摸到或打出的牌面分別計算。
 *
 * @property kinds 規則牌山中存在的每種正規化牌面，依規則牌序排列。
 * @property facesByKind 每種正規化牌面在牌山中出現的實際牌面。
 * @property faceCounts 每一種實際牌面的未見張數。
 */
internal class UnseenTileCounts private constructor(
    val kinds: List<Tile>,
    private val facesByKind: Map<Tile, List<Tile>>,
    private val faceCounts: Map<Tile, Int>,
) {
    /** 所有未見牌的總張數。 */
    val total: Int = faceCounts.values.sum()

    /** 正規化牌面 [kind] 的未見張數。 */
    operator fun get(kind: Tile): Int = facesByKind[kind].orEmpty().sumOf { faceCounts[it] ?: 0 }

    /** 正規化牌面 [kind] 的每一種實際牌面，以及各自的未見張數；未見張數為 0 的牌面不列出。 */
    fun faces(kind: Tile): List<Pair<Tile, Int>> = facesByKind[kind].orEmpty()
        .map { face -> face to (faceCounts[face] ?: 0) }
        .filter { (_, count) -> count > 0 }

    /** 假設摸進一張實際牌面為 [face] 的牌之後的未見張數。 */
    fun without(face: Tile): UnseenTileCounts = UnseenTileCounts(
        kinds = kinds,
        facesByKind = facesByKind,
        faceCounts = faceCounts + (face to ((faceCounts[face] ?: 0) - 1).coerceAtLeast(0)),
    )

    /** [UnseenTileCounts] 的建立方式。 */
    companion object {
        /**
         * 由 [selfId] 的視角計算未見張數。
         *
         * 自己的手牌與副露一律扣除；[countsVisibleTiles] 為 `true` 時再扣除所有牌河中未被鳴走的牌、他家公開的副露與
         * 牌山中已公開的牌。看到的牌面不在規則的牌山組成中時，改從它的正規化牌面扣除。
         */
        fun from(
            snapshot: TableStateSnapshot,
            selfId: Uuid,
            module: MahjongRuleModule<*>,
            countsVisibleTiles: Boolean,
        ): UnseenTileCounts {
            val interpretation = module.createTileInterpretationPolicy()
            val composition = module.createWallFactory().create().getAllTiles().groupingBy { it.tile }.eachCount()
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
            }.groupingBy { face -> if (face in composition) face else interpretation.canonicalize(face) }.eachCount()

            val facesByKind = composition.keys.groupBy { interpretation.canonicalize(it) }
            return UnseenTileCounts(
                kinds = facesByKind.keys.sortedWith(module.tileOrder),
                facesByKind = facesByKind,
                faceCounts = facesByKind.values.fold(emptyMap()) { counts, faces -> counts + unseenFaces(faces, composition, seen) },
            )
        }

        /**
         * 同一種牌 [faces] 各自的未見張數。
         *
         * 某個牌面看到的張數超過它在牌山中的張數時，超出的部分改從同一種牌的其他牌面扣除，使這種牌的未見總數
         * 等於牌山張數減去看到的張數。
         */
        private fun unseenFaces(
            faces: List<Tile>,
            composition: Map<Tile, Int>,
            seen: Map<Tile, Int>,
        ): Map<Tile, Int> {
            val remaining = faces.associateWith { face -> composition.getValue(face) - (seen[face] ?: 0) }
            var overflow = remaining.values.sumOf { (-it).coerceAtLeast(0) }
            return remaining.mapValues { (_, count) ->
                val available = count.coerceAtLeast(0)
                val deducted = minOf(available, overflow)
                overflow -= deducted
                available - deducted
            }
        }
    }
}
