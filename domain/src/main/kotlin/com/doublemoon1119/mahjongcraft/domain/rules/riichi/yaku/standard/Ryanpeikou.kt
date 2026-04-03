package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType

/**
 * 兩杯口 (Ryanpeikou) 役種檢測器。
 *
 * 手牌中有兩個不同的相同順子（例如一個 123 萬和一個 456 萬）。
 * 必須為門前清（無副露），否則不能成立此役種。
 *
 * 與 [calculateIipeikou] 互斥，兩杯口優先於一杯口（3 翻 > 1 翻）。
 * 同時也與七對子互斥，七對子優先於一杯口（2 翻 > 1 翻）。
 *
 * @param handStructure 手牌結構（由 [com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandDecomposer] 分割後的結果）。
 * @param isMenzen 是否為門前清（無副露）。
 * @return 兩杯口役種結果，若不符合則返回 null。
 */
fun calculateRyanpeikou(
    handStructure: HandStructure,
    isMenzen: Boolean
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 兩杯口必須為門前清
    if (!isMenzen) {
        return null
    }

    // 統計順子的數量
    val shuntsuCounts = standard.mentsus
        .mapNotNull { it as? Mentsu.Shuntsu }
        .groupBy { it.headTile }
        .mapValues { it.value.size }

    // 兩杯口：至少有兩個不同的順子，每個至少出現兩次
    // 例如：11223344556677 萬 -> 兩個順子 123 萬 和 456 萬，每個出現兩次
    val distinctShuntsuWithTwoOrMore = shuntsuCounts.count { it.value >= 2 }

    return if (distinctShuntsuWithTwoOrMore >= 2) {
        YakuResult.han(YakuType.Ryanpeikou, 3)
    } else {
        null
    }
}
