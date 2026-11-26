package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.uuid.Uuid

/**
 * 建立初始實體牌牆布局所需的規則中立資料。
 *
 * @property wallLayout 牌牆開門及分區完成後的權威布局結果。
 * @property wallOpening 規則已決定的開門面與開門位置。
 * @property occupiedTileIds 初次發牌完成後仍留在牌牆或規則保留區的牌張，用於避免最終布局碰撞。
 */
data class InitialPhysicalWallLayoutContext(
    val wallLayout: TileWallLayoutResult,
    val wallOpening: WallOpening,
    val occupiedTileIds: Set<Uuid>,
) {
    init {
        require(wallLayout.structure.keys.containsAll(occupiedTileIds)) {
            "Occupied physical wall tiles must belong to the wall structure"
        }
    }
}
