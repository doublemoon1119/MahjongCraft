package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection

/**
 * 包牌責任歸屬。
 *
 * 記錄觸發包牌責任的役滿種類與玩家相對方位，供胡牌結算時判斷點數負擔方式。
 * 結算時由規則依當前座位，把責任方位對應回玩家。
 *
 * @property yaku 觸發包牌的役滿種類。
 * @property direction 觸發包牌的玩家相對方位（鳴牌來源）。
 */
data class RiichiPaoLiability(
    val yaku: RiichiPaoYaku,
    val direction: RelativeDirection,
)
