package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** 規則中立的內建胡牌結算欄位。 */
object BuiltInWinSettlementFieldIds {
    /** 結果標題欄位。 */
    val OUTCOME_TITLE = field("outcome_title")

    /** 贏家摘要欄位。 */
    val WINNER_SUMMARY = field("winner_summary")

    /** 贏家身分欄位。 */
    val WINNER_IDENTITY = field("winner_identity")

    /** 負責玩家身分欄位。 */
    val RESPONSIBLE_PLAYER_IDENTITY = field("responsible_player_identity")

    /** 關係箭頭欄位。 */
    val RELATION_ARROW = field("relation_arrow")

    /** 寶牌標籤欄位。 */
    val DORA_LABEL = field("dora_label")

    /** 裏寶牌標籤欄位。 */
    val URA_DORA_LABEL = field("ura_dora_label")

    /** 完整手牌欄位。 */
    val COMPLETE_HAND = field("complete_hand")

    /** 完整手牌群組欄位。 */
    val COMPLETE_HAND_GROUPS = field("complete_hand_groups")

    /** 和牌張欄位。 */
    val WINNING_TILE = field("winning_tile")

    /** 支付摘要欄位。 */
    val PAYMENT_SUMMARY = field("payment_summary")

    /** 總分欄位。 */
    val TOTAL_SCORE = field("total_score")

    /**
     * 以內建模組命名空間建立結算欄位 ID。
     *
     * @param path 模組命名空間下的欄位路徑。
     * @return 完整 namespaced 欄位 ID。
     */
    private fun field(path: String) = PresentationFieldId("${MinecraftModMetadata.MOD_ID}:$path")
}
