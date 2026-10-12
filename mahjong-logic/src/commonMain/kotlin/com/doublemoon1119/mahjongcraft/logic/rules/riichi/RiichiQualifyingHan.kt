package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType

/**
 * 起胡限制判定使用的非寶牌番數。
 *
 * 寶牌、裏寶牌、赤寶牌與拔北寶牌只影響實際點數，不計入起胡門檻。
 *
 * @property value 非寶牌役種的總番數；役滿時為 [Int.MAX_VALUE]。
 * @property yakuman 是否為役滿。
 * @property isKokushi 是否為國士無雙役滿。
 */
internal data class RiichiQualifyingHan(
    val value: Int,
    val yakuman: Boolean = false,
    val isKokushi: Boolean = false,
) {
    /**
     * 判斷非寶牌番數是否達到起胡門檻；役滿直接符合。
     *
     * @param minimum 要求的最低非寶牌番數。
     * @return 役滿或非寶牌番數達到門檻時為 true。
     */
    fun satisfies(minimum: Int): Boolean = yakuman || value >= minimum
}

/**
 * 由手牌價值結果排除各類寶牌，取得起胡判定使用的番數。
 *
 * @return 非寶牌番數與役滿、國士無雙標記。
 */
internal fun RiichiHandValueResult.qualifyingHan(): RiichiQualifyingHan {
    if (totalHan < 0) {
        return RiichiQualifyingHan(
            value = Int.MAX_VALUE,
            yakuman = true,
            isKokushi = yakuResults.any { it.yaku == YakuType.KokushiMusou || it.yaku == YakuType.KokushiMusou13 },
        )
    }
    return RiichiQualifyingHan(
        value = yakuResults
            .filterNot { it.yaku == YakuType.Dora || it.yaku == YakuType.UraDora || it.yaku == YakuType.AkaDora || it.yaku == YakuType.NukiDora }
            .sumOf { it.han },
    )
}
