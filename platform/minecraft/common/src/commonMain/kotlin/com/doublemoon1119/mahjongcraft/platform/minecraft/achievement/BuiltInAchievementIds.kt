package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 各規則都適用、直接從已提交事實判定的成果 ID。 */
object BuiltInAchievementIds {
    /** 正常完成一場對局。 */
    val MATCH_COMPLETED: String = MahjongCraftMetadata.id("match_completed")

    /** 一場對局依規則排名拿到第一名。 */
    val FIRST_PLACE: String = MahjongCraftMetadata.id("first_place")

    /** 一場對局依規則排名拿到最後一名。 */
    val LAST_PLACE: String = MahjongCraftMetadata.id("last_place")

    /** 胡牌，自摸與胡別人打出的牌都算。 */
    val WIN: String = MahjongCraftMetadata.id("win")

    /** 自摸。 */
    val SELF_DRAW_WIN: String = MahjongCraftMetadata.id("self_draw_win")

    /** 胡別人打出的牌。 */
    val DISCARD_WIN: String = MahjongCraftMetadata.id("discard_win")

    /** 打出的牌被別人胡。 */
    val DEAL_IN: String = MahjongCraftMetadata.id("deal_in")

    /** 打出的牌同時被兩家胡。 */
    val DOUBLE_DEAL_IN: String = MahjongCraftMetadata.id("double_deal_in")

    /** 打出的牌同時被三家胡。 */
    val TRIPLE_DEAL_IN: String = MahjongCraftMetadata.id("triple_deal_in")

    /** 牌山摸完、沒有人胡牌的流局；不含規則提早喊停的途中流局。 */
    val EXHAUSTIVE_DRAW: String = MahjongCraftMetadata.id("exhaustive_draw")
}
