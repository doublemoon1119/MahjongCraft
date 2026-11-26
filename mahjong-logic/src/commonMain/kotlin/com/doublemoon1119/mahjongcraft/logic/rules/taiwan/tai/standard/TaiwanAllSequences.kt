package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.standard

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandDecomposition
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandGroup
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType

/**
 * 平胡：五組都是順子、沒有字牌與花牌、胡別人打出的牌，且不是單吊、中洞或邊張。
 *
 * @param decomposition 這次選用的拆法。
 * @param exposed 已副露的面子。
 * @param allFaces 手牌、副露與胡牌的全部牌面。
 * @param flowers 已補出的花牌面。
 * @param isTsumo 是否自摸。
 * @param isSingleShape 胡牌的面子形狀是否為單吊、中洞或邊張。
 * @return 成立時為兩台。
 */
fun calculateTaiwanAllSequences(
    decomposition: StandardHandDecomposition,
    exposed: List<Meld>,
    allFaces: List<Tile>,
    flowers: List<Tile>,
    isTsumo: Boolean,
    isSingleShape: Boolean,
): TaiwanTaiResult? {
    val winningGroup = decomposition.winningGroupIndex?.let(decomposition.groups::get)
    val allSequences = decomposition.groups.all { it is StandardHandGroup.Sequence } && exposed.all { it.type == MeldType.Chi }
    val applies = allSequences &&
        allFaces.all { it is Tile.Numeric } &&
        flowers.isEmpty() &&
        !isTsumo &&
        winningGroup is StandardHandGroup.Sequence &&
        !isSingleShape
    return TaiwanTaiResult(TaiwanTaiType.AllSequences, 2).takeIf { applies }
}
