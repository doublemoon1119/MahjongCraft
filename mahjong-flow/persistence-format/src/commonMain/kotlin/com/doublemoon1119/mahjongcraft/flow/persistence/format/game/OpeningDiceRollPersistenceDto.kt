package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * 開局程序單輪擲骰結果的保存資料。
 *
 * @property rollIndex 本局中的零基底擲骰索引。
 * @property playerId 實際擲骰玩家 UUID 字串。
 * @property dice 逐顆骰子結果。
 */
@Serializable
data class OpeningDiceRollPersistenceDto(
    val rollIndex: Int,
    val playerId: String,
    val dice: List<Int>,
)
