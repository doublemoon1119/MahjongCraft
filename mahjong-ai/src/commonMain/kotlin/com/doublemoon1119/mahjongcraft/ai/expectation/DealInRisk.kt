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

    /**
     * 打出 [tile] 的放銃期望損失。
     *
     * @param tile 要評估的牌面。
     * @return 列入計算的對手造成的期望損失總和。
     */
    fun immediateLoss(tile: Tile): Double = if (threats.isEmpty()) {
        0.0
    } else {
        lossByTile.getOrPut(tile) {
            threats.sumOf { it.readyProbability * danger(it.opponentId, tile) * it.lossOnDealIn }
        }
    }

    /**
     * [tiles] 各打出一次的平均損失；用於估計還留在手中的牌之後打出的風險。
     *
     * @param tiles 要計算平均損失的牌面清單。
     * @return 每張牌的平均期望損失；清單為空或沒有防守對象時為 0。
     */
    fun averageLoss(tiles: List<Tile>): Double = if (threats.isEmpty() || tiles.isEmpty()) {
        0.0
    } else {
        tiles.sumOf { immediateLoss(it) } / tiles.size
    }

    /**
     * 從未見牌中隨機摸到一張並直接打出的平均損失；用於宣告後不能換牌的手牌。
     *
     * @param unseen 未見牌張數分布。
     * @return 依各牌面未見張數加權的平均期望損失；沒有可見牌或沒有防守對象時為 0。
     */
    fun averageLoss(unseen: UnseenTileCounts): Double = if (threats.isEmpty() || unseen.total == 0) {
        0.0
    } else {
        unseen.kinds.sumOf { kind -> unseen[kind] * immediateLoss(kind) } / unseen.total
    }

    /**
     * 依損失由小到大打出 [count] 張 [tiles] 的總損失；[count] 超過 [tiles] 張數時，超出的部分以平均損失計算。
     *
     * @param tiles 可選擇打出的牌面。
     * @param count 預估要打出的張數；非正數時回傳 0。
     * @return 按上述選牌方式加總的期望損失。
     */
    fun safestLosses(tiles: List<Tile>, count: Int): Double {
        if (threats.isEmpty() || tiles.isEmpty() || count <= 0) return 0.0
        val losses = tiles.map { immediateLoss(it) }.sorted()
        val taken = losses.take(count).sum()
        val remaining = (count - losses.size).coerceAtLeast(0)
        return taken + remaining * losses.average()
    }

    /**
     * 列入防守計算中最小的一張牌損失；用於「接下來總要打出一張牌」的比較。
     *
     * @param tiles 可選擇打出的牌面。
     * @return 最低的期望損失；清單為空時為 0。
     */
    fun minimumLoss(tiles: List<Tile>): Double = tiles.minOfOrNull { immediateLoss(it) } ?: 0.0
}
