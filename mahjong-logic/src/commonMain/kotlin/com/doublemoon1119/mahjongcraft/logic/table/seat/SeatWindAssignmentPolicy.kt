package com.doublemoon1119.mahjongcraft.logic.table.seat

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/** 依規則將本局所有玩家指派至唯一自風的 policy。 */
fun interface SeatWindAssignmentPolicy {
    /**
     * 產生本局自風；回傳值必須恰好涵蓋所有玩家，且不可重複使用風位。
     *
     * @param context 本局玩家順序、莊家、骰子與開門資訊。
     * @return 玩家 Uuid 至本局自風的完整映射。
     */
    fun assign(context: SeatWindAssignmentContext): Map<Uuid, Wind>
}

/**
 * 執行 policy 並驗證規則回傳的映射可安全套用到桌況。
 *
 * @param context 本局自風指派上下文。
 * @return 通過完整性與唯一性檢查的自風映射。
 */
fun SeatWindAssignmentPolicy.assignValidated(context: SeatWindAssignmentContext): Map<Uuid, Wind> {
    val assignment = assign(context)
    val expectedPlayerIds = context.playerIdsInTurnOrder.toSet()
    require(assignment.keys == expectedPlayerIds) {
        "Seat wind assignment must contain exactly the table players; " +
            "missing=${expectedPlayerIds - assignment.keys}, unknown=${assignment.keys - expectedPlayerIds}"
    }
    require(assignment.values.distinct().size == assignment.size) {
        "Seat wind assignment must not contain duplicate winds"
    }
    return assignment
}
