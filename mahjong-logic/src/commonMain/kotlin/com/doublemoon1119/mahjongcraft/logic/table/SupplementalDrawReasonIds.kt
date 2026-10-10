package com.doublemoon1119.mahjongcraft.logic.table

/** 跨規則共用的補牌決策原因 ID。 */
object SupplementalDrawReasonIds {
    /** 補牌所需的牌牆來源已耗盡。 */
    const val WALL_EXHAUSTED = "mahjongcraft:wall_exhausted"

    /** Policy 結果未通過呼叫端的牌張守恆或引用驗證。 */
    const val INVALID_RESULT = "mahjongcraft:invalid_supplemental_draw_result"
}
