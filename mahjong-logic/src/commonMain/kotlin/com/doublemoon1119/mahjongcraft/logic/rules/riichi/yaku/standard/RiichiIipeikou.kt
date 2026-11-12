package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiHandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiMentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType

/**
 * 一杯口 (Iipeikou) 役種檢測器。
 *
 * 手牌中有兩個相同的順子（例如兩個 123 萬）。
 * 必須為門前清（無副露），否則不能成立此役種。
 *
 * 與 [calculateRiichiRyanpeikou] 互斥，兩杯口優先於一杯口。
 *
 * @param handStructure 手牌結構（由 [RiichiHandDecomposer] 分割後的結果）。
 * @param isMenzen 是否為門前清（無副露）。
 * @return 一杯口役種結果，若不符合則返回 null。
 */
fun calculateRiichiIipeikou(
    handStructure: RiichiHandStructure,
    isMenzen: Boolean,
): RiichiYakuResult? {
    val standard = handStructure as? RiichiHandStructure.Standard ?: return null

    // 一杯口必須為門前清
    if (!isMenzen) {
        return null
    }

    // 統計順子的數量
    val shuntsuCounts = standard.mentsus
        .mapNotNull { it as? RiichiMentsu.Shuntsu }
        .groupBy { it.headTile }
        .mapValues { it.value.size }

    // 檢查是否有兩個相同的順子
    val hasIipeikou = shuntsuCounts.values.any { it >= 2 }

    return if (hasIipeikou) {
        RiichiYakuResult.han(RiichiYakuType.Iipeikou, 1)
    } else {
        null
    }
}
