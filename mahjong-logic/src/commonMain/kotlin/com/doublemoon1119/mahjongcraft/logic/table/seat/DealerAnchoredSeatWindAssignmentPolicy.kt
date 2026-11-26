package com.doublemoon1119.mahjongcraft.logic.table.seat

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

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
