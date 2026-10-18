package com.doublemoon1119.mahjongcraft.logic.table.opening

import kotlin.uuid.Uuid

/**
 * 開局程序下一步要求的擲骰。
 *
 * @property playerId 實際擲骰的玩家。
 * @property diceCount 這次要求的骰子數量。
 */
data class OpeningDiceRollRequest(
    val playerId: Uuid,
    val diceCount: Int,
)
