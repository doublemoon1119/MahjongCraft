package com.doublemoon1119.mahjongcraft.logic.module

/** 一種和牌方式的價值。 */
sealed interface WinValue {
    /**
     * 可以和牌。
     *
     * @property points 和牌可得的點數。
     */
    data class Points(val points: Int) : WinValue {
        init {
            require(points >= 0) { "Win value must not be negative" }
        }
    }

    /** 依規則不能以這種方式和牌，例如沒有役或未達起胡條件。 */
    data object NotWinnable : WinValue

    /** 規則無法從這個視角判斷。 */
    data object Unknown : WinValue
}
