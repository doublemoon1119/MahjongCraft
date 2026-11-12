package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.standard

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandDecomposition
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandGroup
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType

/**
 * 碰碰胡：五組都是刻子或槓子。
 *
 * @param decomposition 這次選用的拆法。
 * @param exposed 已副露的面子。
 * @return 成立時為四台。
 */
fun calculateTaiwanAllTriplets(decomposition: StandardHandDecomposition, exposed: List<Meld>): TaiwanTaiResult? = TaiwanTaiResult(TaiwanTaiType.AllTriplets, 4).takeIf {
    decomposition.groups.all { it is StandardHandGroup.Triplet } && exposed.none { it.type == MeldType.Chi }
}
