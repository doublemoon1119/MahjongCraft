package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandDecomposer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.CompletionType
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 三暗刻 (Sanankou) 役種檢測器。
 *
 * 三暗刻是立直麻將中的二翻役：含有三組暗面子（暗刻或暗槓），可以有其他副露。
 *
 * 暗面子是指在手中自行湊成的三張或四張相同牌組：
 * - 手牌中的刻子是暗刻；但雙碰聽牌榮和時，靠別人捨牌湊成的那組刻子是明刻。
 * - 副露中只有暗槓是暗面子；碰、明槓、加槓都是明面子。
 *
 * @param handStructure 手牌結構（由 [RiichiHandDecomposer] 分割後的結果）。
 * @param isTsumo 是否為自摸。
 * @return 三暗刻役種結果，若不符合則返回 null。
 */
fun calculateSanankou(
    handStructure: HandStructure,
    isTsumo: Boolean,
): YakuResult? {
    val standard = handStructure as? HandStructure.Standard ?: return null

    // 雙碰聽牌時，胡牌張所在的刻子一定在手牌面子中；榮和時這組刻子是明刻，要扣掉
    val ronCompletedTripletCount = if (standard.completionType is CompletionType.Shanpon && !isTsumo) 1 else 0

    // 計算暗面子數量：手牌中的暗刻 (Kotsu) + 副露中的暗槓 (Ankan)
    val ankouCount = standard.mentsus.count { mentsu ->
        mentsu is Mentsu.Kotsu
    } + standard.fuuro.count { fuuro ->
        fuuro.mentsu is Mentsu.Ankan
    } - ronCompletedTripletCount

    // 三暗刻需要至少 3 組暗面子
    if (ankouCount >= 3) {
        return YakuResult.han(YakuType.Sanankou, 2)
    }

    return null
}
