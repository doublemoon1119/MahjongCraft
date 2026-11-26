package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/** 歷史和牌明細值在網路與持久化格式中使用的穩定種類識別碼。 */
object HistoryDetailValueTypeKeys {
    /** 有單位數值明細的種類識別碼。 */
    const val QUANTITIES: String = "quantities"

    /** 不含牌張參照的牌面清單種類。 */
    const val TILE_FACES: String = "tile_faces"

    /** 條列明細的種類識別碼。 */
    const val ENTRIES: String = "entries"
}
