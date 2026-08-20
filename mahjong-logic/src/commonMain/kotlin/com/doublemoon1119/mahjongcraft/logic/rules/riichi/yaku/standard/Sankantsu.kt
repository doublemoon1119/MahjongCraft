package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 三杠子 (Sankantsu) 役種檢測器。
 *
 * 三杠子是立直麻將中的二翻役，條件如下：
 * 手牌中含有三組杠子（槓）。
 *
 * 杠子包括：
 * - 暗槓 (Ankan)：在手中暗地槓牌
 * - 明槓 (Minkan)：鳴取他人的牌進行槓
 * - 加槓 (Kakan)：在已有的碰基礎上增加一張牌進行槓
 *
 * @param handStructure 手牌結構（由 [RiichiHandDecomposer] 分割後的結果）。
 * @return 三杠子役種結果，若不符合則返回 null。
 */
fun calculateSankantsu(
    handStructure: HandStructure,
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 收集所有面子（手牌 + 副露）
    val allMentsus = standard.mentsus + standard.fuuro.map { it.mentsu }

    // 計算槓的數量：暗槓 (Ankan) + 明槓 (Minkan) + 加槓 (Kakan)
    val kanCount = allMentsus.count { mentsu ->
        mentsu is Mentsu.Ankan ||
            mentsu is Mentsu.Minkan ||
            mentsu is Mentsu.Kakan
    }

    // 三杠子需要剛好 3 組槓
    if (kanCount == 3) {
        return YakuResult.han(YakuType.Sankantsu, 2)
    }

    return null
}
