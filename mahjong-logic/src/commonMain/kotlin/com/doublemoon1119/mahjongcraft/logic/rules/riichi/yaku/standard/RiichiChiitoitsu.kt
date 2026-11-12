package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiHandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType

/**
 * 七對子 (Chiitoitsu) 役種檢測器。
 *
 * 手牌由七個對子組成（14 張牌）。
 * 必須為門前清（無副露），否则不能成立此役種。
 *
 * @param handStructure 手牌結構（由 [RiichiHandDecomposer] 分割後的結果）。
 * @param isMenzen 是否為門前清（無副露）。
 * @return 七對子役種結果，若不符合則返回 null。
 */
fun calculateRiichiChiitoitsu(
    handStructure: RiichiHandStructure,
    isMenzen: Boolean,
): RiichiYakuResult? {
    if (handStructure !is RiichiHandStructure.Chiitoitsu) {
        return null
    }

    // 七對子必須為門前清
    if (!isMenzen) {
        return null
    }

    return RiichiYakuResult.han(RiichiYakuType.Chiitoitsu, 2)
}
