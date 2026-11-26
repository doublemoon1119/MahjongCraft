package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.OpenedTileWallOrder
import com.doublemoon1119.mahjongcraft.logic.table.TileWallArrangement
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/**
 * 把整副牌排成開門之後依序配出指定的手牌、摸到指定的牌，保留牌區的指定位置放指定的牌。
 *
 * - 先以同一副牌開門一次，找出洗牌順序中每個位置開門後會落在摸牌順序或保留牌區的哪裡。
 * - 再把指定的牌放到對應的位置。
 * - 開門與布局完全沿用規則的計算。
 *
 * @property round 照腳本進行的這一局。
 * @property dealBatchSizes 配牌時每一輪各家依序抓的張數。
 * @property playerCount 人數。
 */
class HistoryFixtureWallArrangement(
    private val round: HistoryFixtureRound,
    private val dealBatchSizes: List<Int>,
    private val playerCount: Int,
) : TileWallArrangement {
    override fun arrange(shuffled: List<IdentifiedTile>, open: (List<IdentifiedTile>) -> OpenedTileWallOrder): List<IdentifiedTile> {
        val wantedDraws = dealingOrder() + HistoryFixtureTile.parse(round.draws)
        val wantedReserved = round.reserved.mapValues { (_, notation) -> HistoryFixtureTile.parse(notation).single() }
        val pool = shuffled.toMutableList()
        val assignedDraws = wantedDraws.map { spec -> take(pool, spec) }
        val assignedReserved = wantedReserved.mapValues { (_, spec) -> take(pool, spec) }
        val probe = open(shuffled)
        require(wantedDraws.size <= probe.drawOrder.size) { "The fixture asks for more draws than the wall holds" }
        require(wantedReserved.keys.all { it in probe.reservedWallTiles.indices }) { "A reserved position is outside the reserved tiles" }
        val placed = mutableMapOf<Uuid, IdentifiedTile>()
        probe.drawOrder.forEachIndexed { index, slot -> assignedDraws.getOrNull(index)?.let { placed[slot.id] = it } }
        probe.reservedWallTiles.forEachIndexed { index, slot -> assignedReserved[index]?.let { placed[slot.id] = it } }
        val fillers = pool.iterator()
        val arranged = shuffled.map { slot -> placed[slot.id] ?: fillers.next() }
        val opened = open(arranged)
        check(opened.drawOrder.take(wantedDraws.size).zip(wantedDraws).all { (tile, spec) -> spec.matches(tile) }) {
            "The arranged wall does not deal the fixture tiles"
        }
        check(wantedReserved.all { (index, spec) -> spec.matches(opened.reservedWallTiles[index]) }) {
            "The arranged wall does not reserve the fixture tiles"
        }
        return arranged
    }

    /**
     * 配牌時依序從牌山摸出的牌：每一輪由莊家開始，各家依序抓這一輪的張數。
     *
     * @return 配牌的摸牌順序。
     */
    private fun dealingOrder(): List<HistoryFixtureTile> {
        val winds = Wind.entries.take(playerCount)
        require(round.hands.keys == winds.toSet()) { "Every seat needs a hand: $winds" }
        val remaining = winds.associateWith { wind -> HistoryFixtureTile.parse(round.hands.getValue(wind)).toMutableList() }
        require(remaining.values.all { it.size == dealBatchSizes.sum() }) { "Each hand must hold ${dealBatchSizes.sum()} tiles" }
        return dealBatchSizes.flatMap { batch ->
            winds.flatMap { wind -> List(batch) { remaining.getValue(wind).removeFirst() } }
        }
    }

    /**
     * 從剩下的牌中取出一張符合指定的牌。
     *
     * @param pool 還沒有用到的牌。
     * @param spec 指定的牌。
     * @return 取出的牌。
     * @throws IllegalArgumentException 剩下的牌中沒有這張牌時拋出。
     */
    private fun take(pool: MutableList<IdentifiedTile>, spec: HistoryFixtureTile): IdentifiedTile {
        val index = pool.indexOfFirst(spec::matches)
        require(index >= 0) { "The fixture uses more copies of $spec than the wall holds" }
        return pool.removeAt(index)
    }
}
