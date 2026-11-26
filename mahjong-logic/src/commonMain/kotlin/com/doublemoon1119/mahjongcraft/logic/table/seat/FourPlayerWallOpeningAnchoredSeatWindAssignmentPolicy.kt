package com.doublemoon1119.mahjongcraft.logic.table.seat

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

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
