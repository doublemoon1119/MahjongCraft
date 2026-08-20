package com.doublemoon1119.mahjongcraft.logic.table.opening

import com.doublemoon1119.mahjongcraft.logic.table.TileWall

/**
 * 一局牌牆的規則開門位置。
 *
 * 此模型保留實體牌牆的方位與墩數語意，不直接保存線性 [TileWall] 索引。
 * 完整牌山如何映射成線性摸牌順序，由後續牌山 layout 負責。
 *
 * @property wallSideOffsetFromDealer 從莊家牌牆面開始，依玩家回合的逆時針方向計算的零基底偏移；
 * `0` 為莊家面前、`1` 為南家面前、`2` 為西家面前、`3` 為北家面前。
 * @property stacksFromRight 從該面玩家視角的右端開始計算，到開門缺口為止的一基底墩數。
 */
data class WallOpening(
    val wallSideOffsetFromDealer: Int,
    val stacksFromRight: Int,
) {
    init {
        require(wallSideOffsetFromDealer >= 0) { "Wall side offset must not be negative" }
        require(stacksFromRight > 0) { "Stacks from right must be positive" }
    }
}
