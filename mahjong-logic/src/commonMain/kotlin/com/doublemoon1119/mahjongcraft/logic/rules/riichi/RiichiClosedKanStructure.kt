package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical

/** 檢查立直手牌的所有標準和牌拆法，確認要暗槓的三張牌只能作為刻子。 */
internal object RiichiClosedKanStructure {
    /**
     * 對每種等待牌列舉雀頭與面子；任何拆法把槓牌用於雀頭或順子時，便不允許暗槓。
     *
     * 僅檢查暗槓前的立牌，已有暗槓以面子數計入；不判斷役種或暗槓後的等待牌。
     *
     * @param standingTiles 不含本次摸牌的立牌。
     * @param winningTiles 暗槓前的完整等待牌集合。
     * @param kanTile 本次摸入並用來暗槓的牌種。
     * @param existingMelds 已有的面子數。
     * @return 每種等待都有標準和牌拆法，且槓牌在所有拆法中只能作刻子時為 true。
     */
    fun preservesTriplet(
        standingTiles: List<Tile>,
        winningTiles: Set<Tile>,
        kanTile: Tile,
        existingMelds: Int,
    ): Boolean {
        val canonicalTiles = standingTiles.map { it.riichiCanonical }
        val baseTile = kanTile.riichiCanonical
        val requiredMelds = 4 - existingMelds
        if (requiredMelds !in 1..4 || canonicalTiles.size != requiredMelds * 3 + 1) return false
        if (canonicalTiles.count { it == baseTile } != 3 || winningTiles.isEmpty()) return false
        return winningTiles.all { waitingTile ->
            val counts = (canonicalTiles + waitingTile.riichiCanonical).groupingBy { it }.eachCount().toMutableMap()
            var interpretations = 0
            for (pair in counts.keys.toList()) {
                interpretations = interpretations or withGroup(counts, listOf(pair, pair)) {
                    searchMelds(counts, baseTile, requiredMelds, pair == baseTile)
                }
                if (interpretations and ALTERNATE_INTERPRETATION != 0) break
            }
            interpretations == TRIPLET_ONLY
        }
    }

    /**
     * 取完所有牌才能成立和牌拆法；優先搜尋刻子，再搜尋包含目前牌張的所有順子。
     *
     * @param counts 尚未使用的牌種計數，遞迴返回前還原。
     * @param kanTile 要檢查的槓牌牌種。
     * @param remainingMelds 尚需組成的面子數。
     * @param alternate 是否已將槓牌用於雀頭或順子。
     * @return 有效拆法的位元集合；找到替代解釋即可提早停止。
     */
    private fun searchMelds(
        counts: MutableMap<Tile, Int>,
        kanTile: Tile,
        remainingMelds: Int,
        alternate: Boolean,
    ): Int {
        if (remainingMelds == 0) {
            return if (counts.values.any { it != 0 }) {
                0
            } else if (alternate) {
                ALTERNATE_INTERPRETATION
            } else {
                TRIPLET_ONLY
            }
        }
        val first = counts.entries.firstOrNull { it.value > 0 }?.key ?: return 0
        var result = withGroup(counts, listOf(first, first, first)) {
            searchMelds(counts, kanTile, remainingMelds - 1, alternate)
        }
        if (result and ALTERNATE_INTERPRETATION != 0 || first !is Tile.Numeric) return result
        for (start in maxOf(1, first.value - 2)..minOf(7, first.value)) {
            val sequence = (start..start + 2).map { Tile.Numeric(first.suit, it) }
            result = result or withGroup(counts, sequence) {
                searchMelds(counts, kanTile, remainingMelds - 1, alternate || kanTile in sequence)
            }
            if (result and ALTERNATE_INTERPRETATION != 0) return result
        }
        return result
    }

    /**
     * 暫時取走指定組合；牌張不足時跳過，搜尋結束後還原計數。
     *
     * @param counts 目前可使用的牌種計數。
     * @param tiles 要取走的雀頭、刻子或順子。
     * @param search 取走組合後的拆牌搜尋。
     * @return 搜尋結果；牌張不足時為零。
     */
    private inline fun withGroup(counts: MutableMap<Tile, Int>, tiles: List<Tile>, search: () -> Int): Int {
        val required = tiles.groupingBy { it }.eachCount()
        if (required.any { (tile, amount) -> counts.getOrDefault(tile, 0) < amount }) return 0
        required.forEach { (tile, amount) -> counts[tile] = counts.getValue(tile) - amount }
        return try {
            search()
        } finally {
            required.forEach { (tile, amount) -> counts[tile] = counts.getValue(tile) + amount }
        }
    }

    /** 槓牌只作為刻子的有效標準拆法。 */
    private const val TRIPLET_ONLY = 1

    /** 槓牌可作為雀頭或順子的有效標準拆法。 */
    private const val ALTERNATE_INTERPRETATION = 2
}
