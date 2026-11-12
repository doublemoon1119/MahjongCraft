package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 規則逐輪把手上的牌移出並補牌時，其中一輪公開移出的牌，例如使用花牌的規則補花。
 *
 * @property round 輪次，從 1 開始。
 * @property removals 這一輪各玩家移出的牌，依處理順序排列；同一位玩家在同一輪只出現一次。
 */
data class TileReplacementRound(
    val round: Int,
    val removals: List<TileReplacementRemoval>,
) {
    init {
        require(round >= 1) { "Replacement round must start at one" }
        require(removals.isNotEmpty()) { "Replacement round must remove at least one tile" }
        require(removals.map { it.playerId }.distinct().size == removals.size) { "Each player appears once per replacement round" }
    }
}
