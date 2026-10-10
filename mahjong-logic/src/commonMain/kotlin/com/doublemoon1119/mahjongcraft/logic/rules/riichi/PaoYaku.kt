package com.doublemoon1119.mahjongcraft.logic.rules.riichi

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
    Daisuushii,
}
