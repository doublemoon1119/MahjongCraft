package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.standard

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandDecomposition
import com.doublemoon1119.mahjongcraft.logic.judgment.StandardHandGroup
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType

/**
 * 三暗刻、四暗刻與五暗刻：暗槓算暗刻；胡別人打出的牌時，胡牌那組刻子不算暗刻。
 *
 * @param decomposition 這次選用的拆法。
 * @param exposed 已副露的面子。
 * @param isTsumo 是否自摸。
 * @return 三暗刻兩台、四暗刻五台、五暗刻八台；其他情況為 null。
 */
fun calculateTaiwanConcealedTriplets(
    decomposition: StandardHandDecomposition,
    exposed: List<Meld>,
    isTsumo: Boolean,
): TaiwanTaiResult? {
    val concealedCount = decomposition.groups.withIndex().count { (index, group) ->
        group is StandardHandGroup.Triplet && (isTsumo || index != decomposition.winningGroupIndex)
    } + exposed.count { it.type == MeldType.ClosedKan }
    return when (concealedCount) {
        3 -> TaiwanTaiResult(TaiwanTaiType.ThreeConcealedTriplets, 2)
        4 -> TaiwanTaiResult(TaiwanTaiType.FourConcealedTriplets, 5)
        5 -> TaiwanTaiResult(TaiwanTaiType.FiveConcealedTriplets, 8)
        else -> null
    }
}
