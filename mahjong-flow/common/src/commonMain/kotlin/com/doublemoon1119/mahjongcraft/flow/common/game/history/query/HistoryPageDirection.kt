package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/** 歷史清單從游標往哪一邊取一頁。 */
enum class HistoryPageDirection {
    /** 取游標之後的項目。沒有游標時從第一筆開始。 */
    AFTER,

    /** 取游標之前的項目。沒有游標時取最後一頁。 */
    BEFORE,
}
