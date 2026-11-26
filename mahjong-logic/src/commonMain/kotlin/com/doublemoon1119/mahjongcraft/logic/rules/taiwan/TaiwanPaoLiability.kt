package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection

/**
 * 玩家身上成立的包牌責任。
 *
 * @property hand 觸發包牌的牌型。
 * @property direction 打出那張牌的玩家相對於被包玩家的方位。
 */
data class TaiwanPaoLiability(
    val hand: TaiwanPaoHand,
    val direction: RelativeDirection,
)
