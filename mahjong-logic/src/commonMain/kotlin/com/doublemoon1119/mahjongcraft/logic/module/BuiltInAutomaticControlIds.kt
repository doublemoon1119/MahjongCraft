package com.doublemoon1119.mahjongcraft.logic.module

/** MahjongCraft 內建的本局自動操作控制 ID。 */
object BuiltInAutomaticControlIds {
    /** 有合法和牌動作時自動提交和牌。 */
    const val AUTO_WIN = "mahjongcraft:auto_win"

    /** 自動拒絕規則分類為可拒絕鳴牌的反應。 */
    const val DECLINE_CALLS = "mahjongcraft:decline_calls"

    /** 規則允許時自動打出本巡剛摸進的牌。 */
    const val AUTO_TSUMOGIRI = "mahjongcraft:auto_tsumogiri"
}
