package com.doublemoon1119.mahjongcraft.logic.table.opening

import com.doublemoon1119.mahjongcraft.logic.table.TileWall

/**
 * 一局牌牆的規則開門位置。
 *
 * 此模型保留實體牌牆的方位與墩數語意，不直接保存線性 [TileWall] 索引。
 * 完整牌山如何映射成線性摸牌順序，由後續牌山 layout 負責。
 *
 * @property wallSideOffsetFromDealer 規則牌牆布局中的零基底面索引。日麻以莊家面前為起點排列，
 * 其他規則可使用固定側面；實際方位由規則布局決定，不能只依欄位名稱推算。
 * @property stacksFromRight 從桌外面向該面時的右端開始計算，到開門缺口為止的一基底墩數。
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
