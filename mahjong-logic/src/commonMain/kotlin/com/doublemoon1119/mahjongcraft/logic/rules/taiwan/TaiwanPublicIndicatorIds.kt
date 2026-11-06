package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 台灣麻將宣告聽牌以外的公開標示識別碼；宣告聽牌的標示見 [TaiwanReadyKind.indicatorId]。 */
object TaiwanPublicIndicatorIds {
    /** 莊家的連莊次數，只標在連莊一次以上的莊家身上。 */
    val DEALER_STREAK: String = MahjongCraftMetadata.id("taiwan/dealer_streak_indicator")
}
