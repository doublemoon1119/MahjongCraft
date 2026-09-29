package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.uuid.Uuid

/**
 * 一位列入防守計算的對手。
 *
 * @property opponentId 對手 Uuid。
 * @property readyProbability 對手已經聽牌的可能性。
 * @property lossOnDealIn 放銃給這位對手時的損失點數。
 */
internal data class OpponentThreat(
    val opponentId: Uuid,
    val readyProbability: Double,
    val lossOnDealIn: Double,
)

/**
 * 打出一張牌的放銃期望損失。
 *
 * 每張牌的損失為每位列入計算的對手 ×（聽牌可能性 × 此牌危險度 × 放銃損失）的總和。
 *
 * @property threats 列入防守計算的對手；為空時所有損失皆為 0。
 * @property danger 已經聽牌的對手以一張牌榮和的可能性。
 */
internal class DealInRisk(
    private val threats: List<OpponentThreat>,
    private val danger: (opponentId: Uuid, tile: Tile) -> Double,
) {
    /** 同一張牌的損失在一次決策內只計算一次。 */
    private val lossByTile = HashMap<Tile, Double>()

    /** 打出 [tile] 的放銃期望損失。 */
    fun immediateLoss(tile: Tile): Double = if (threats.isEmpty()) {
        0.0
    } else {
        lossByTile.getOrPut(tile) {
            threats.sumOf { it.readyProbability * danger(it.opponentId, tile) * it.lossOnDealIn }
        }
    }

    /** [tiles] 各打出一次的平均損失；用於估計還留在手中的牌之後打出的風險。 */
    fun averageLoss(tiles: List<Tile>): Double = if (threats.isEmpty() || tiles.isEmpty()) {
        0.0
    } else {
        tiles.sumOf { immediateLoss(it) } / tiles.size
    }

    /** 從未見牌中隨機摸到一張並直接打出的平均損失；用於宣告後不能換牌的手牌。 */
    fun averageLoss(unseen: UnseenTileCounts): Double = if (threats.isEmpty() || unseen.total == 0) {
        0.0
    } else {
        unseen.kinds.sumOf { kind -> unseen[kind] * immediateLoss(kind) } / unseen.total
    }

    /** 列入防守計算中最小的一張牌損失；用於「接下來總要打出一張牌」的比較。 */
    fun minimumLoss(tiles: List<Tile>): Double = tiles.minOfOrNull { immediateLoss(it) } ?: 0.0
}
