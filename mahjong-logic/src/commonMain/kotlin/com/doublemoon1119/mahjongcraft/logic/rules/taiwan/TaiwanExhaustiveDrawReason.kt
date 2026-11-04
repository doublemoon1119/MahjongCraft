package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 台灣麻將的流局原因。 */
sealed class TaiwanExhaustiveDrawReason : ExhaustiveDrawReason {
    /** 一般牌牆摸盡。 */
    data object Normal : TaiwanExhaustiveDrawReason() {
        override val id: String = MahjongCraftMetadata.id("taiwan/exhaustive_draw/normal")
    }

    /** 公開花牌後已經沒有合法補牌。 */
    data object NoSupplementalTile : TaiwanExhaustiveDrawReason() {
        override val id: String = MahjongCraftMetadata.id("taiwan/exhaustive_draw/no_supplemental_tile")
    }

    /** 全桌合計完成四個槓後，無人以嶺上牌或隨後捨牌和牌。 */
    data object FourKans : TaiwanExhaustiveDrawReason() {
        override val id: String = MahjongCraftMetadata.id("taiwan/exhaustive_draw/four_kans")
    }

    /** 第一巡四位玩家依序打出相同風牌，且期間無人鳴牌。 */
    data object FourWinds : TaiwanExhaustiveDrawReason() {
        override val id: String = MahjongCraftMetadata.id("taiwan/exhaustive_draw/four_winds")
    }
}
