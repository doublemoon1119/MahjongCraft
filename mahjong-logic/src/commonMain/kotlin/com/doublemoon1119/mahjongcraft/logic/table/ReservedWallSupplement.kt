package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch

/**
 * 從保留區靠開門缺口的一端補牌，並將相鄰的可用牌納入保留區。
 *
 * @param state 補牌前的桌況。
 * @return 補牌結果；任一牌區沒有可取的牌時為 null。
 */
internal fun drawFromReservedWall(state: TableState): SupplementalDrawDecision.Completed? {
    val drawn = state.reservedWallTiles.firstOrNull() ?: return null
    val replenishment = state.tileWall.drawLast()
    val transferred = replenishment.tile ?: return null
    return SupplementalDrawDecision.Completed(
        drawnTiles = listOf(drawn),
        tileWall = replenishment.wall,
        reservedWallTiles = state.reservedWallTiles.drop(1) + transferred,
        tableComponents = ComponentPatch(),
    )
}
