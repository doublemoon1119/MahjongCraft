package com.doublemoon1119.mahjongcraft.logic.table.opening

import kotlin.uuid.Uuid

/**
 * 本局開局程序中一次由指定玩家完成的權威擲骰。
 *
 * @property rollIndex 本局擲骰的零基底順序。
 * @property playerId 實際擲骰的玩家。
 * @property dice 這次每顆骰子的點數。
 */
data class OpeningDiceRoll(
    val rollIndex: Int,
    val playerId: Uuid,
    val dice: DiceRollResult,
) {
    init {
        require(rollIndex >= 0) { "Opening dice roll index must not be negative" }
    }
}
