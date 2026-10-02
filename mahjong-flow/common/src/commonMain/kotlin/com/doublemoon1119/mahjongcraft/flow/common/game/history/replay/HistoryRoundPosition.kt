package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/** 歷史 Replay 局內位置。 */
sealed interface HistoryRoundPosition {
    /** 開局位置。 */
    data object Initial : HistoryRoundPosition

    /** 已套用指定交易的位置。
     * @property index 交易索引。
     */
    data class AfterTransaction(val index: Int) : HistoryRoundPosition
}
