package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiHandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.RiichiMentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiYakuType

/**
 * 對對胡 (Toitoi) 役種檢測器。
 *
 * 對對胡是立直麻將中的二翻役，條件如下：
 * 1. 手牌由四組刻子（Kotsu）或槓（Ankan、Minkan、Kakan）組成
 * 2. 剩餘一組為雀頭（對子）
 *
 * 與七對子不同，對對胡允許副露（鳴牌）。
 * 亦即：手牌（不含副露）部分可為 1 雀頭 + 1 刻子，其餘 3 組刻子可透過副露達成。
 *
 * @param handStructure 手牌結構（由 [RiichiHandDecomposer] 分割後的結果）。
 * @return 對對胡役種結果，若不符合則返回 null。
 */
fun calculateRiichiToitoi(
    handStructure: RiichiHandStructure,
): RiichiYakuResult? {
    val standard = handStructure as? RiichiHandStructure.Standard ?: return null

    // 收集所有的面子（包括手牌中的面子與副露）
    val allMentsus = standard.mentsus + standard.fuuro.map { it.mentsu }

    // 計算刻子與槓的數量，需剛好 4 組
    val ponCount = allMentsus.count { mentsu ->
        mentsu is RiichiMentsu.Kotsu ||
            mentsu is RiichiMentsu.Ankan ||
            mentsu is RiichiMentsu.Minkan ||
            mentsu is RiichiMentsu.Kakan
    }

    if (ponCount != 4) {
        return null
    }

    return RiichiYakuResult.han(RiichiYakuType.Toitoi, 2)
}
