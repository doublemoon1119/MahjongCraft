package com.doublemoon1119.mahjongcraft.logic.table.seat

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.uuid.Uuid

/**
 * 指派一局自風時可使用的權威上下文。
 *
 * @property playerIdsInTurnOrder 整場固定、依回合方向排列的玩家 Uuid。
 * @property dealerPlayerId 本局權威莊家 Uuid。
 * @property diceRoll 本局擲骰結果；規則未使用骰子時為 `null`。
 * @property wallOpening 本局牌牆開門結果；規則未建立實體牌牆開門時為 `null`。
 */
data class SeatWindAssignmentContext(
    val playerIdsInTurnOrder: List<Uuid>,
    val dealerPlayerId: Uuid,
    val diceRoll: DiceRollResult?,
    val wallOpening: WallOpening?,
) {
    init {
        require(playerIdsInTurnOrder.size in MIN_PLAYER_COUNT..MAX_PLAYER_COUNT) {
            "Seat wind assignment supports $MIN_PLAYER_COUNT..$MAX_PLAYER_COUNT players"
        }
        require(playerIdsInTurnOrder.distinct().size == playerIdsInTurnOrder.size) {
            "Player turn order must not contain duplicate players"
        }
        require(dealerPlayerId in playerIdsInTurnOrder) { "Dealer must belong to the table" }
    }

    private companion object {
        /** 核心自風模型支援的最少玩家數。 */
        const val MIN_PLAYER_COUNT: Int = 2

        /** 核心自風模型支援的最多玩家數。 */
        const val MAX_PLAYER_COUNT: Int = 4
    }
}

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

/** 以本局莊家為東，依回合順序分配東、南、西、北的通用 policy。 */
object DealerAnchoredSeatWindAssignmentPolicy : SeatWindAssignmentPolicy {
    override fun assign(context: SeatWindAssignmentContext): Map<Uuid, Wind> {
        val playerIds = context.playerIdsInTurnOrder
        val dealerIndex = playerIds.indexOf(context.dealerPlayerId)
        return List(playerIds.size) { offset ->
            playerIds[(dealerIndex + offset) % playerIds.size] to Wind.entries[offset]
        }.toMap()
    }
}

/**
 * 四人牌局專用的開門定風 policy：開門位置的玩家為東，其餘風位沿回合順序排列。
 *
 * 此 policy 刻意拒絕三人桌，因為四面實體牌牆的 offset 不能直接對三位玩家取餘數。
 */
object FourPlayerWallOpeningAnchoredSeatWindAssignmentPolicy : SeatWindAssignmentPolicy {
    override fun assign(context: SeatWindAssignmentContext): Map<Uuid, Wind> {
        val playerIds = context.playerIdsInTurnOrder
        require(playerIds.size == FOUR_PLAYER_COUNT) { "Wall-opening-anchored seat winds require exactly four players" }
        val wallOpening = requireNotNull(context.wallOpening) { "Wall opening is required for wall-opening-anchored seat winds" }
        val dealerIndex = playerIds.indexOf(context.dealerPlayerId)
        val eastIndex = (dealerIndex + wallOpening.wallSideOffsetFromDealer).mod(playerIds.size)
        return List(playerIds.size) { offset ->
            playerIds[(eastIndex + offset) % playerIds.size] to Wind.entries[offset]
        }.toMap()
    }

    private const val FOUR_PLAYER_COUNT: Int = 4
}
