package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.standard

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType

/**
 * 混一色與清一色：數牌只有一種花色；有字牌為混一色，沒有字牌為清一色。
 *
 * @param allFaces 手牌、副露與胡牌的全部牌面。
 * @return 混一色四台、清一色八台；其他情況為 null。
 */
fun calculateTaiwanFlush(allFaces: List<Tile>): TaiwanTaiResult? {
    val suits = allFaces.filterIsInstance<Tile.Numeric>().map { it.suit }.toSet()
    if (suits.size != 1) return null
    return if (allFaces.any { it is Tile.Honor }) TaiwanTaiResult(TaiwanTaiType.HalfFlush, 4) else TaiwanTaiResult(TaiwanTaiType.FullFlush, 8)
}
