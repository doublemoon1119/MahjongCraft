package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.history

/** 開始生成的安全結果，不含 SQL 或原始例外。 */
sealed interface HistoryGenerationStartResult {
    /** 已啟動一批工作。 */
    data object Started : HistoryGenerationStartResult

    /** 原有批次仍執行中。 */
    data object Busy : HistoryGenerationStartResult

    /** 不支援指定情境。 */
    data object InvalidScenario : HistoryGenerationStartResult

    /** 尚未綁定存檔 session。 */
    data object SessionUnavailable : HistoryGenerationStartResult

    /** 總開關或 AI 篩選拒絕新場。 */
    data object PolicyRejected : HistoryGenerationStartResult

    /** writer 斷線或容量已停止。 */
    data object StorageUnavailable : HistoryGenerationStartResult
}
