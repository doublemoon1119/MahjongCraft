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

    /** 台數單位。 */
    val TAI_UNIT: String = MahjongCraftMetadata.id("taiwan/unit/tai")
}
