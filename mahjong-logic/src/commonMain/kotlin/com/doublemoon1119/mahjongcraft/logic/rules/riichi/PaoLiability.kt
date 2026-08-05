package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection

/**
 * 包牌（責任払い）的適用役滿種類。
 *
 * 依循 M League 公式競技規則，包牌僅適用於大三元、大四喜，
 * 且僅限於「透過鳴牌（碰/明槓）成立」的情境；大明槓後槓上開花不適用包牌。
 */
enum class PaoYaku {
    /** 大三元。 */
    Daisangen,

    /** 大四喜。 */
    Daisuushii
}

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
    val direction: RelativeDirection
)
