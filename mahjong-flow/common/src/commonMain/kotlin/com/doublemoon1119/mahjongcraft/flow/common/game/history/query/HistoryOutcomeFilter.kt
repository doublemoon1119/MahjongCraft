package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/** 對局的結束狀態篩選。 */
enum class HistoryOutcomeFilter {
    /** 只顯示正常完成的對局。 */
    COMPLETED,

    /** 只顯示有可靠終止證據的中斷對局。 */
    INTERRUPTED,
}
