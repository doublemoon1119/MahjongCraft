package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku

import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueResult

/**
 * 役種計算結果。
 *
 * @property yaku 檢測到的役種。
 * @property han 該役種提供的番數（役滿為 -1，雙倍役滿為 -2）。
 * @property isYakuman 是否為役滿。
 * @property isDoubleYakuman 是否為雙倍役滿。
 */
data class YakuResult(
    val yaku: YakuType,
    val han: Int,
    val isYakuman: Boolean = han < 0,
    val isDoubleYakuman: Boolean = han == -2
) {
    companion object {
        /** 一般役滿（1 倍） */
        fun yakuman(yaku: YakuType) = YakuResult(yaku, -1)

        /** 雙倍役滿（2 倍） */
        fun doubleYakuman(yaku: YakuType) = YakuResult(yaku, -2)

        /** 一般番數 */
        fun han(yaku: YakuType, han: Int) = YakuResult(yaku, han)
    }
}

/**
 * 手牌役種計算的完整結果。
 *
 * @property yakuResults 各役種的計算結果列表。
 * @property totalHan 總番數（役滿時為 -1，雙倍役滿時為 -2，以此類推）。
 * @property isYakuman 是否為役滿。
 * @property isCompleteHand 是否為已完成的手牌（可胡牌）。
 */
data class HandYakuResult(
    val yakuResults: List<YakuResult>,
    val totalHan: Int,
    val isYakuman: Boolean = totalHan < 0,
    val isCompleteHand: Boolean = true
) : HandValueResult {
    override val totalValue: Int get() = totalHan

    override val isSpecial: Boolean get() = isYakuman
}
