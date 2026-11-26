package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/** AI 參與狀態篩選。 */
enum class HistoryAiFilter {
    /** 只顯示包含 AI 的對局。 */
    CONTAINS_AI,

    /** 只顯示沒有 AI 的對局。 */
    NO_AI,
}
