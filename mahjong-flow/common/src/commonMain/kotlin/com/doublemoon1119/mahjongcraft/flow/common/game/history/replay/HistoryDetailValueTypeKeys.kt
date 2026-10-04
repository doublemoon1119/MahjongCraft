package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/** 歷史和牌明細值在網路與持久化格式中使用的穩定種類識別碼。 */
object HistoryDetailValueTypeKeys {
    /** 翻譯文字明細的種類識別碼。 */
    const val TEXT: String = "text"

    /** 牌種明細的種類識別碼。 */
    const val TILES: String = "tiles"

    /** 條列明細的種類識別碼。 */
    const val ENTRIES: String = "entries"
}
