package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 台灣麻將補花流程對外可辨識的規則選擇 ID。 */
object TaiwanFlowerRuleDecisionIds {
    /** 自動處理局前花牌。 */
    val PROCESS_FLOWERS: String = MahjongCraftMetadata.id("taiwan/process_flowers")

    /** 自動接續摸牌後的花牌處理。 */
    val PROCESS_DRAW_FOLLOW_UP: String = MahjongCraftMetadata.id("taiwan/process_draw_follow_up")

    /** 選擇是否接受特殊花牌和牌。 */
    val SPECIAL_FLOWER_WIN: String = MahjongCraftMetadata.id("taiwan/special_flower_win")

    /** 放棄特殊花牌和牌。 */
    val PASS_FLOWER_WIN: String = MahjongCraftMetadata.id("taiwan/pass_flower_win")
}
