package com.doublemoon1119.mahjongcraft.logic.table.seat

import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.uuid.Uuid

/**
 * 指派一局自風時可使用的權威上下文。
 *
 * @property playerIdsInTurnOrder 本局依回合方向排列的玩家 Uuid。
 * @property dealerPlayerId 本局權威莊家 Uuid。
 * @property diceRolls 本局完整擲骰記錄；規則未使用骰子時為空清單。
 * @property wallOpening 本局牌牆開門結果；規則未建立實體牌牆開門時為 `null`。
 */
data class SeatWindAssignmentContext(
    val playerIdsInTurnOrder: List<Uuid>,
    val dealerPlayerId: Uuid,
    val diceRolls: List<OpeningDiceRoll>,
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
