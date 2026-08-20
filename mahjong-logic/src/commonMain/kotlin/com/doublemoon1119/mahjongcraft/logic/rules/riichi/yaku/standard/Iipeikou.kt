package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 一杯口 (Iipeikou) 役種檢測器。
 *
 * 手牌中有兩個相同的順子（例如兩個 123 萬）。
 * 必須為門前清（無副露），否則不能成立此役種。
 *
 * 與 [calculateRyanpeikou] 互斥，兩杯口優先於一杯口。
 *
 * @param handStructure 手牌結構（由 [RiichiHandDecomposer] 分割後的結果）。
 * @param isMenzen 是否為門前清（無副露）。
 * @return 一杯口役種結果，若不符合則返回 null。
 */
fun calculateIipeikou(
    handStructure: HandStructure,
    isMenzen: Boolean,
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 一杯口必須為門前清
    if (!isMenzen) {
        return null
    }

    // 統計順子的數量
    val shuntsuCounts = standard.mentsus
        .mapNotNull { it as? Mentsu.Shuntsu }
        .groupBy { it.headTile }
        .mapValues { it.value.size }

    // 檢查是否有兩個相同的順子
    val hasIipeikou = shuntsuCounts.values.any { it >= 2 }

    return if (hasIipeikou) {
        YakuResult.han(YakuType.Iipeikou, 1)
    } else {
        null
    }
}
