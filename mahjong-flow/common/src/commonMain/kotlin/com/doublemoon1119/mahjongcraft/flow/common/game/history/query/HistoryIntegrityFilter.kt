package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/** 對局完整性篩選。 */
enum class HistoryIntegrityFilter {
    /** 只顯示可完整重播的對局。 */
    COMPLETE,

    /** 只顯示缺少部分內容、但仍有終止摘要的對局。 */
    INCOMPLETE,
}
