package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/** 歷史查詢的頁面、識別碼與篩選條件限制。 */
object HistoryQueryLimits {
    /** 預設歷史清單頁大小。 */
    const val DEFAULT_PAGE_SIZE: Int = 20

    /** 歷史清單允許的最小頁大小。 */
    const val MIN_PAGE_SIZE: Int = 1

    /** 歷史清單允許的最大頁大小。 */
    const val MAX_PAGE_SIZE: Int = 50

    /** 歷史查詢規則 ID 的最大長度。 */
    const val MAX_RULE_ID_LENGTH: Int = 255

    /** 歷史查詢玩家名稱片段的最大長度。 */
    const val MAX_PLAYER_NAME_LENGTH: Int = 16

    /** 歷史查詢對局 ID 文字的最大長度。 */
    const val MAX_MATCH_ID_LENGTH: Int = 64

    /** 歷史事件頁允許的最小交易數量。 */
    const val MIN_REPLAY_PAGE_SIZE: Int = 1

    /** 歷史事件頁允許的最大交易數量。 */
    const val MAX_REPLAY_PAGE_SIZE: Int = 20

    /** 歷史事件頁的預設交易數量。 */
    const val DEFAULT_REPLAY_PAGE_SIZE: Int = 20
}
