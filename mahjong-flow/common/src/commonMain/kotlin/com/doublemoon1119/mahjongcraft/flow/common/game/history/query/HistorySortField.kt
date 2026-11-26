package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/** 歷史清單的排序欄位。 */
enum class HistorySortField {
    /** 依結束時間排序。 */
    ENDED_AT,

    /** 依對局持續時間排序。 */
    DURATION,

    /** 依目前使用者的終局名次排序。 */
    OWN_RANK,

    /** 依目前使用者的終局分數排序。 */
    OWN_SCORE,
}
