package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 規則中立的內建胡牌結算欄位。 */
object BuiltInWinSettlementFieldIds {
    /** 結果標題欄位。 */
    val OUTCOME_TITLE = PresentationFieldId(MahjongCraftMetadata.id("outcome_title"))

    /** 贏家摘要欄位。 */
    val WINNER_SUMMARY = PresentationFieldId(MahjongCraftMetadata.id("winner_summary"))

    /** 贏家身分欄位。 */
    val WINNER_IDENTITY = PresentationFieldId(MahjongCraftMetadata.id("winner_identity"))

    /** 負責玩家身分欄位。 */
    val RESPONSIBLE_PLAYER_IDENTITY = PresentationFieldId(MahjongCraftMetadata.id("responsible_player_identity"))

    /** 關係箭頭欄位。 */
    val RELATION_ARROW = PresentationFieldId(MahjongCraftMetadata.id("relation_arrow"))

    /** 完整手牌欄位。 */
    val COMPLETE_HAND = PresentationFieldId(MahjongCraftMetadata.id("complete_hand"))

    /** 完整手牌群組欄位。 */
    val COMPLETE_HAND_GROUPS = PresentationFieldId(MahjongCraftMetadata.id("complete_hand_groups"))

    /** 和牌張欄位。 */
    val WINNING_TILE = PresentationFieldId(MahjongCraftMetadata.id("winning_tile"))

    /** 支付摘要欄位。 */
    val PAYMENT_SUMMARY = PresentationFieldId(MahjongCraftMetadata.id("payment_summary"))

    /** 總分欄位。 */
    val TOTAL_SCORE = PresentationFieldId(MahjongCraftMetadata.id("total_score"))
}
