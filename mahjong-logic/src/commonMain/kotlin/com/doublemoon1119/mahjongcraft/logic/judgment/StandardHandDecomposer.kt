package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/** 列舉若干面子加一組雀頭的全部拆法，不判定任何地區的台數或特殊牌型。 */
object StandardHandDecomposer {
    /**
     * 將不含和牌張的立牌與和牌張合併，列舉拆法與和牌張可能的位置。
     *
     * 已宣告的槓算一組面子，不在立牌中重新拆槓；本函式不讀取或修改任何遊戲狀態。
     *
     * @param handTiles 不含和牌張與已宣告副露的立牌。
     * @param winningTile 獨立傳入的和牌張。
     * @param exposedMeldCount 已宣告的面子數，包含暗槓。
     * @param targetMelds 完整和牌需要的面子數，支援1至5。
     * @return 所有不同拆法；牌數、牌種或立牌張數不符時為空。
     */
    fun decompose(
        handTiles: List<Tile>,
        winningTile: Tile,
        exposedMeldCount: Int,
        targetMelds: Int,
    ): List<StandardHandDecomposition> {
        require(targetMelds in 1..5) { "Target meld count must be between 1 and 5" }
        if (exposedMeldCount !in 0..targetMelds) return emptyList()
        val tiles = handTiles + winningTile
        val needed = targetMelds - exposedMeldCount
        if (tiles.size != needed * 3 + 2 || tiles.any { it !is Tile.Numeric && it !is Tile.Honor }) return emptyList()
        val counts = tiles.groupingBy { it }.eachCount().toMutableMap()
        if (counts.values.any { it > 4 }) return emptyList()
        val results = mutableListOf<StandardHandDecomposition>()
        for (pair in counts.keys.sortedBy(::tileOrder)) {
            if (counts.getValue(pair) < 2) continue
            counts[pair] = counts.getValue(pair) - 2
            val partitions = mutableListOf<List<StandardHandGroup>>()
            findGroups(
                counts = counts,
                needed = needed,
                groups = mutableListOf(),
                results = partitions,
            )
            for (groups in partitions) {
                if (pair == winningTile) {
                    results += StandardHandDecomposition(
                        pair = pair,
                        groups = groups,
                        winningGroupIndex = null,
                    )
                }
                groups.forEachIndexed { index, group ->
                    if (winningTile in group.tiles) {
                        results += StandardHandDecomposition(
                            pair = pair,
                            groups = groups,
                            winningGroupIndex = index,
                        )
                    }
                }
            }
            counts[pair] = counts.getValue(pair) + 2
        }
        return results.distinct()
    }

    /**
     * 從尚未使用的最小牌開始，列舉所有刻子與順子的組合。
     *
     * @param counts 剩餘牌數，遞迴返回時還原。
     * @param needed 尚需的面子數。
     * @param groups 已選取的面子。
     * @param results 收集完整拆法的位置。
     */
    private fun findGroups(
        counts: MutableMap<Tile, Int>,
        needed: Int,
        groups: MutableList<StandardHandGroup>,
        results: MutableList<List<StandardHandGroup>>,
    ) {
        val tile = counts.keys.filter { counts.getValue(it) > 0 }.minByOrNull(::tileOrder)
        if (tile == null) {
            if (needed == 0) results += groups.toList()
            return
        }
        if (needed == 0) return
        val choices = mutableListOf<StandardHandGroup>()
        if (counts.getValue(tile) >= 3) choices += StandardHandGroup.Triplet(tile)
        if (tile is Tile.Numeric && tile.value <= 7) {
            val sequence = StandardHandGroup.Sequence(tile)
            if (sequence.tiles.all { (counts[it] ?: 0) > 0 }) choices += sequence
        }
        for (group in choices) {
            group.tiles.forEach { counts[it] = counts.getValue(it) - 1 }
            groups += group
            findGroups(
                counts = counts,
                needed = needed - 1,
                groups = groups,
                results = results,
            )
            groups.removeLast()
            group.tiles.forEach { counts[it] = counts.getValue(it) + 1 }
        }
    }

    /**
     * 取得固定的牌面排序，讓同一手牌的列舉順序不受輸入排列影響。
     *
     * @param tile 數牌或字牌。
     * @return 數牌先於字牌的排序值。
     */
    private fun tileOrder(tile: Tile): Int = when (tile) {
        is Tile.Numeric -> tile.suit.ordinal * 9 + tile.value - 1
        Tile.Honor.East -> 27
        Tile.Honor.South -> 28
        Tile.Honor.West -> 29
        Tile.Honor.North -> 30
        Tile.Honor.Red -> 31
        Tile.Honor.Green -> 32
        Tile.Honor.White -> 33
        is Tile.Extension -> error("Extension tiles do not form standard groups")
    }
}
