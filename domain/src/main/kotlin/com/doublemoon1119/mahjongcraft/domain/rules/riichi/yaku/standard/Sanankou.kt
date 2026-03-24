package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType

/**
 * 三暗刻 (Sanankou) 役種檢測器。
 *
 * 三暗刻是立直麻將中的二翻役，條件如下：
 * 1. 手牌中含有三組暗面子（暗刻或暗槓）
 * 2. 暗面子可來自手牌或副露（暗槓）
 *
 * 暗面子是指在手中自行湊成的三張或四張相同牌組，未透過副露（鳴牌）取得。
 * 暗槓雖然是副露，但仍視為暗面子。
 *
 * @param handStructure 手牌結構（由 [com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandDecomposer] 分割後的結果）。
 * @return 三暗刻役種結果，若不符合則返回 null。
 */
fun calculateSanankou(
    handStructure: HandStructure?
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 收集所有面子（手牌 + 副露）
    val allMentsus = standard.mentsus + standard.fuuro.map { it.mentsu }

    // 計算暗面子數量：暗刻 (Kotsu) + 暗槓 (Ankan)
    val ankouCount = allMentsus.count { mentsu ->
        mentsu is Mentsu.Kotsu || mentsu is Mentsu.Ankan
    }

    // 三暗刻需要至少 3 組暗面子
    if (ankouCount >= 3) {
        return YakuResult.han(YakuType.Sanankou, 2)
    }

    return null
}
