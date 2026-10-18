package com.doublemoon1119.mahjongcraft.logic.table.opening

import kotlin.uuid.Uuid

/**
 * 開局程序已得到起家與開門結果。
 *
 * @property dealerPlayerId 本局最終莊家。
 * @property wallOpening 本局牌牆開門位置；不使用牌牆開門時為 null。
 */
data class RoundOpeningCompletion(
    val dealerPlayerId: Uuid,
    val wallOpening: WallOpening?,
)
