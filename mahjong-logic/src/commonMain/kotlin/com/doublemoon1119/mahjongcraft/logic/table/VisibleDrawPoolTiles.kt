package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy

/**
 * 統計剩餘張數時需要扣除的可見牌，不把仍可能摸到的公開牌牆牌扣掉。
 *
 * 玩家區域的可見牌都列入；牌牆中的牌只有在規則公開表明不能摸取時才列入。
 * 未知牌不參與判斷，也不檢查權威牌牆的隱藏用途。
 *
 * @param interpretation 本局規則對已知牌面與公開用途的解讀方式。
 * @return 依牌張 ID 去重、已保留可知計算特性的牌張。
 */
fun TableStateSnapshot.visibleTilesOutsideDrawPool(interpretation: TileInterpretationPolicy): List<IdentifiedTile> {
    val possiblyDrawableIds = tileWall.tiles.filter { tile ->
        tile.face?.let { !interpretation.isUnavailableForDraw(it) } == true
    }.mapTo(mutableSetOf()) { it.id }
    return visibleTiles(interpretation).filterNot { it.id in possiblyDrawableIds }
}
