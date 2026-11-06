package com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 台灣麻將胡牌結算詳情使用的穩定識別碼。 */
object TaiwanWinSettlementIds {
    /** 台種與台數列表欄位。 */
    val TAI_FIELD: String = MahjongCraftMetadata.id("taiwan/win_settlement/tai")

    /** 合計台數欄位。 */
    val TAI_TOTAL_FIELD: String = MahjongCraftMetadata.id("taiwan/win_settlement/tai_total")

    /** 贏家公開的花牌欄位；特殊花牌和牌不含讓和牌成立的那張。 */
    val FLOWERS_FIELD: String = MahjongCraftMetadata.id("taiwan/win_settlement/flowers")

    /** 讓特殊花牌和牌成立的那張花牌欄位。 */
    val WINNING_FLOWER_FIELD: String = MahjongCraftMetadata.id("taiwan/win_settlement/winning_flower")

    /** 閒家自摸時莊家那一份的合計台數欄位。 */
    val DEALER_TAI_TOTAL_FIELD: String = MahjongCraftMetadata.id("taiwan/win_settlement/dealer_tai_total")

    /** 初莊時的莊家台條目。 */
    val DEALER_ENTRY: String = MahjongCraftMetadata.id("taiwan/tai/dealer")

    /** 連莊時合併莊家、連莊與拉莊的條目；名稱數值是連莊次數。 */
    val DEALER_STREAK_ENTRY: String = MahjongCraftMetadata.id("taiwan/tai/dealer_streak")

    /** 連莊次數單位。 */
    val DEALER_STREAK_UNIT: String = MahjongCraftMetadata.id("taiwan/unit/dealer_streak")

    /** 台數單位。 */
    val TAI_UNIT: String = MahjongCraftMetadata.id("taiwan/unit/tai")
}
