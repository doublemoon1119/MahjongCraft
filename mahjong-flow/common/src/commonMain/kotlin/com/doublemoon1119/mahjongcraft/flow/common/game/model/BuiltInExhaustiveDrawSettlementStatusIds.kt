package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 內建回合結算玩家狀態 ID。 */
object BuiltInExhaustiveDrawSettlementStatusIds {
    /** 一般荒牌流局的聽牌者。 */
    val TENPAI: String = MahjongCraftMetadata.id("tenpai")

    /** 一般荒牌流局的未聽者。 */
    val NOTEN: String = MahjongCraftMetadata.id("noten")

    /** 需要公開手牌證明流局宣告成立，但不代表聽牌。 */
    val DRAW_DECLARATION: String = MahjongCraftMetadata.id("draw_declaration")
}
