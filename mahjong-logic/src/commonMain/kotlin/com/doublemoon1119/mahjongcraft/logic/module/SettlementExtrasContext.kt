package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 一次結算時決定場上供託與連莊點數歸屬的情境。
 *
 * @property tableState 套用本次結算前的桌況。
 * @property occasion 分配的時機。
 * @property candidatePlayerIds 可以收款的玩家，依規則中立的優先順序排列：和牌時從放銃者的下家起算（自摸只有和牌者），
 * 流局時從莊家起算，整場結束時依最終排名。不會是空清單。
 * @property stickPotAmount 這次收下的供託總額，來自 [MahjongRuleModule.collectStickPot]；規則沒有供託機制時為 0。
 */
data class SettlementExtrasContext(
    val tableState: TableState,
    val occasion: SettlementExtrasOccasion,
    val candidatePlayerIds: List<Uuid>,
    val stickPotAmount: Int,
) {
    init {
        require(candidatePlayerIds.isNotEmpty()) { "Settlement extras need at least one candidate" }
        require(candidatePlayerIds.distinct().size == candidatePlayerIds.size) { "Settlement extras candidates must be distinct" }
        require(stickPotAmount >= 0) { "Stick pot amount must not be negative" }
    }
}
