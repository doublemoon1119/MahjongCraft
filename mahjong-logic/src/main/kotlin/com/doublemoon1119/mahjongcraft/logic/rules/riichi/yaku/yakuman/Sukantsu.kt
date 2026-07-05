package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 四杠子 (Sukantsu / Four Quads) 役滿檢測器。
 *
 * 四杠子是立直麻將中的役滿，條件如下：
 * 手牌由四個面子加一個雀頭組成，且這四個面子全部都是槓子。
 *
 * 槓子類型：
 * - 暗槓 (Ankan)：在手中暗地槓牌
 * - 明槓 (Minkan)：鳴取他人的牌進行槓
 * - 加槓 (Kakan)：在已有的碰基礎上增加一張牌進行槓
 *
 * 牌型範例：
 * - 面子：1m 1m 1m 1m、9m 9m 9m 9m、5s 5s 5s 5s、發 發 發 發（暗槓）
 * - 雀頭：東 東
 *
 * @param handStructure 手牌結構（由 [com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer] 分割後的結果）。
 * @return 四杠子役滿結果，若不符合則返回 null。
 */
fun calculateSukantsu(
    handStructure: HandStructure
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

    // 四杠子需要 4 個面子全部都是槓子
    if (kanCount == 4 && allMentsus.size == 4) {
        return YakuResult.yakuman(YakuType.Sukantsu)
    }

    return null
}
