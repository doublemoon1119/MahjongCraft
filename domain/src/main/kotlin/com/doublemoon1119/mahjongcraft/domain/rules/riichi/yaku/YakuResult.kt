package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku

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
