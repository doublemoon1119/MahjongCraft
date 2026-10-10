package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 同一權威交易中唯一的桌況結果。
 */
sealed interface HistoryTableResult {
    /**
     * 可根據前一桌況還原的變化。
     *
     * @property change 本次交易的桌況差異。
     */
    data class Change(
        val change: HistoryTableChange,
    ) : HistoryTableResult

    /**
     * 無法安全表示結構變化時的完整檢查點。
     *
     * @property reasonId 選用完整檢查點的穩定原因 ID。
     * @property tableState 交易提交後的完整桌況。
     */
    data class Checkpoint(
        val reasonId: String,
        val tableState: TableState,
    ) : HistoryTableResult
}
