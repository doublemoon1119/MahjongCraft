package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection

/**
 * 包牌責任歸屬。
 *
 * 記錄觸發包牌責任的役滿種類與玩家相對方位，供胡牌結算時判斷點數負擔方式。
 * 此類別刻意不記錄玩家的絕對身分（如 Uuid），因為 `:mahjong-logic` 不持有玩家身分的概念；
 * 呼叫端（如未來的 Game Use Case）需自行將 [direction] 對應回實際玩家。
 *
 * @property yaku 觸發包牌的役滿種類。
 * @property direction 觸發包牌的玩家相對方位（鳴牌來源）。
 */
data class PaoLiability(
    val yaku: PaoYaku,
    val direction: RelativeDirection,
)
