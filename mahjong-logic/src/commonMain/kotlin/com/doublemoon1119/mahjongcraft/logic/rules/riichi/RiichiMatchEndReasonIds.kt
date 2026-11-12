package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 日本立直麻將（含三人）專屬的整場終止原因識別碼。 */
object RiichiMatchEndReasonIds {
    /** 最後莊家和牌或聽牌後已居第一，並符合終局點數而結束整場。 */
    val DEALER_TOP_FINISH: String = MahjongCraftMetadata.id("riichi/match_end/dealer_top_finish")
}
