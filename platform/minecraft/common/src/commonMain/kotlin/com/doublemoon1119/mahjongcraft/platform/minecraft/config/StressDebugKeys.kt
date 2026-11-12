package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** 壓力測試 debug 指令共用的本地化翻譯鍵。 */
object StressDebugKeys {
    /** 已開始壓力測試。 */
    const val STARTED: String = "mahjongcraft.debug.stress.started"

    /** 在單人世界的內建伺服器上開始時，提醒數據僅供參考。 */
    const val INTEGRATED_SERVER_HINT: String = "mahjongcraft.debug.stress.integrated_server_hint"

    /** 已有壓力測試執行中。 */
    const val BUSY: String = "mahjongcraft.debug.stress.busy"

    /** 對局情境不存在。 */
    const val INVALID_SCENARIO: String = "mahjongcraft.debug.stress.invalid_scenario"

    /** 推進節奏不存在。 */
    const val INVALID_PACE: String = "mahjongcraft.debug.stress.invalid_pace"

    /** 歷史處理方式不存在。 */
    const val INVALID_HISTORY_MODE: String = "mahjongcraft.debug.stress.invalid_history_mode"

    /** 不認得的選項，參數為寫錯的名稱與可用的選項。 */
    const val UNKNOWN_OPTION: String = "mahjongcraft.debug.stress.unknown_option"

    /** 選項缺少值，參數為選項名稱。 */
    const val MISSING_OPTION_VALUE: String = "mahjongcraft.debug.stress.missing_option_value"

    /** 選項重複，參數為選項名稱。 */
    const val DUPLICATE_OPTION: String = "mahjongcraft.debug.stress.duplicate_option"

    /** 數值選項超出範圍，參數為選項名稱、最小值與最大值。 */
    const val INVALID_OPTION_NUMBER: String = "mahjongcraft.debug.stress.invalid_option_number"

    /** 壓力測試資料庫無法開啟。 */
    const val STORAGE_UNAVAILABLE: String = "mahjongcraft.debug.stress.storage_unavailable"

    /** 沒有執行中的壓力測試。 */
    const val NOT_RUNNING: String = "mahjongcraft.debug.stress.not_running"

    /** 還沒有任何壓力測試報告。 */
    const val NO_REPORT: String = "mahjongcraft.debug.stress.no_report"

    /** 已刪除壓力測試資料庫。 */
    const val CLEARED: String = "mahjongcraft.debug.stress.cleared"

    /** 沒有壓力測試資料庫可刪除。 */
    const val NOTHING_TO_CLEAR: String = "mahjongcraft.debug.stress.nothing_to_clear"

    /** 壓力測試仍在執行，不能刪除資料庫。 */
    const val CLEAR_WHILE_RUNNING: String = "mahjongcraft.debug.stress.clear_while_running"

    /** 執行中報告的標題。 */
    const val TITLE_RUNNING: String = "mahjongcraft.debug.stress.report.title_running"

    /** 已停止報告的標題。 */
    const val TITLE_STOPPED: String = "mahjongcraft.debug.stress.report.title_stopped"

    /** 對局情境欄位。 */
    const val SCENARIO: String = "mahjongcraft.debug.stress.report.scenario"

    /** 桌數安排欄位。 */
    const val MODE: String = "mahjongcraft.debug.stress.report.mode"

    /** 固定桌數的安排，參數為桌數。 */
    const val MODE_FIXED: String = "mahjongcraft.debug.stress.report.mode.fixed"

    /** 爬坡的安排。 */
    const val MODE_RAMP: String = "mahjongcraft.debug.stress.report.mode.ramp"

    /** 推進節奏欄位。 */
    const val PACE: String = "mahjongcraft.debug.stress.report.pace"

    /** 真實節奏。 */
    const val PACE_REALTIME: String = "mahjongcraft.debug.stress.report.pace.realtime"

    /** 全速節奏。 */
    const val PACE_FAST: String = "mahjongcraft.debug.stress.report.pace.fast"

    /** 歷史處理方式欄位。 */
    const val HISTORY_MODE: String = "mahjongcraft.debug.stress.report.history_mode"

    /** 歷史處理方式：寫入資料庫。 */
    const val HISTORY_MODE_WRITE: String = "mahjongcraft.debug.stress.report.history_mode.write"

    /** 歷史處理方式：只記錄並編碼。 */
    const val HISTORY_MODE_ENCODE: String = "mahjongcraft.debug.stress.report.history_mode.encode"

    /** 歷史處理方式：不記錄。 */
    const val HISTORY_MODE_OFF: String = "mahjongcraft.debug.stress.report.history_mode.off"

    /** 同時進行桌數欄位。 */
    const val TABLES: String = "mahjongcraft.debug.stress.report.tables"

    /** 已完成場數欄位。 */
    const val COMPLETED: String = "mahjongcraft.debug.stress.report.completed"

    /** 卡住中止場數欄位。 */
    const val FAILED: String = "mahjongcraft.debug.stress.report.failed"

    /** 經過時間欄位。 */
    const val ELAPSED: String = "mahjongcraft.debug.stress.report.elapsed"

    /** 暖機狀態欄位。 */
    const val WARMUP: String = "mahjongcraft.debug.stress.report.warmup"

    /** 暖機中，參數為剩餘秒數。 */
    const val WARMUP_RUNNING: String = "mahjongcraft.debug.stress.report.warmup.running"

    /** 暖機已結束，參數為排除的秒數。 */
    const val WARMUP_DONE: String = "mahjongcraft.debug.stress.report.warmup.done"

    /** 這次測試沒有暖機。 */
    const val WARMUP_NONE: String = "mahjongcraft.debug.stress.report.warmup.none"

    /** 每 tick 耗時欄位。 */
    const val TICK: String = "mahjongcraft.debug.stress.report.tick"

    /** 每 tick 耗時超過各門檻的比例欄位。 */
    const val SLOW_TICKS: String = "mahjongcraft.debug.stress.report.slow_ticks"

    /** 開始持續卡頓時的桌數欄位，參數為卡頓的毫秒門檻。 */
    const val STUTTER: String = "mahjongcraft.debug.stress.report.stutter"

    /** 尚未發生持續卡頓。 */
    const val STUTTER_NONE: String = "mahjongcraft.debug.stress.report.stutter.none"

    /** 實際 tick 間隔欄位。 */
    const val TICK_INTERVAL: String = "mahjongcraft.debug.stress.report.tick_interval"

    /** 相對每 tick 50 ms 節奏的落後量欄位。 */
    const val LAG: String = "mahjongcraft.debug.stress.report.lag"

    /** 每秒 MahjongCraft 推進耗時欄位。 */
    const val ADVANCE_PER_SECOND: String = "mahjongcraft.debug.stress.report.advance_per_second"

    /** 單步耗時欄位。 */
    const val STEP: String = "mahjongcraft.debug.stress.report.step"

    /** 單步耗時拆解欄位。 */
    const val STEP_BREAKDOWN: String = "mahjongcraft.debug.stress.report.step_breakdown"

    /** 單步環節：AI 視角。 */
    const val STEP_STAGE_AI_CONTEXT: String = "mahjongcraft.debug.stress.report.step_stage.ai_context"

    /** 單步環節：規則與狀態。 */
    const val STEP_STAGE_RULES_AND_STATE: String = "mahjongcraft.debug.stress.report.step_stage.rules_and_state"

    /** 單步環節：快照同步。 */
    const val STEP_STAGE_SNAPSHOT_SYNC: String = "mahjongcraft.debug.stress.report.step_stage.snapshot_sync"

    /** 單步環節：AI 思考。 */
    const val STEP_STAGE_AI_DECISION: String = "mahjongcraft.debug.stress.report.step_stage.ai_decision"

    /** 每 tick 歷史記錄耗時欄位。 */
    const val HISTORY_RECORDING: String = "mahjongcraft.debug.stress.report.history_recording"

    /** AI 決策延遲欄位。 */
    const val AI_LATENCY: String = "mahjongcraft.debug.stress.report.ai_latency"

    /** AI 決策數與使用固定命令的次數欄位。 */
    const val AI_DECISIONS: String = "mahjongcraft.debug.stress.report.ai_decisions"

    /** 過期而沒有套用的 AI 決策數欄位。 */
    const val STALE_DECISIONS: String = "mahjongcraft.debug.stress.report.stale_decisions"

    /** 尚未結束的策略工作數欄位。 */
    const val STRATEGY_CALLS: String = "mahjongcraft.debug.stress.report.strategy_calls"

    /** 單一 tick 內最多步數與歷史事件數欄位。 */
    const val PEAK_PER_TICK: String = "mahjongcraft.debug.stress.report.peak_per_tick"

    /** 歷史事件產生與處理數量欄位。 */
    const val EVENTS: String = "mahjongcraft.debug.stress.report.events"

    /** 歷史背景工作耗時拆解欄位。 */
    const val WRITER_BREAKDOWN: String = "mahjongcraft.debug.stress.report.writer_breakdown"

    /** 歷史背景工作環節：磁碟用量檢查。 */
    const val WRITER_STAGE_DISK_USAGE: String = "mahjongcraft.debug.stress.report.writer_stage.disk_usage"

    /** 歷史背景工作環節：對局狀態同步。 */
    const val WRITER_STAGE_DECISION_SYNC: String = "mahjongcraft.debug.stress.report.writer_stage.decision_sync"

    /** 歷史背景工作環節：已刪除對局檢查。 */
    const val WRITER_STAGE_TOMBSTONES: String = "mahjongcraft.debug.stress.report.writer_stage.tombstones"

    /** 歷史背景工作環節：編碼。 */
    const val WRITER_STAGE_ENCODE: String = "mahjongcraft.debug.stress.report.writer_stage.encode"

    /** 歷史背景工作環節：批次寫入。 */
    const val WRITER_STAGE_BATCH_WRITE: String = "mahjongcraft.debug.stress.report.writer_stage.batch_write"

    /** 歷史背景工作環節：牌譜封存。 */
    const val WRITER_STAGE_ARCHIVE: String = "mahjongcraft.debug.stress.report.writer_stage.archive"

    /** 歷史背景工作環節：定期維護。 */
    const val WRITER_STAGE_MAINTENANCE: String = "mahjongcraft.debug.stress.report.writer_stage.maintenance"

    /** 歷史背景工作環節：儲存統計。 */
    const val WRITER_STAGE_STORAGE_STATS: String = "mahjongcraft.debug.stress.report.writer_stage.storage_stats"

    /** 歷史待寫佇列欄位。 */
    const val PENDING: String = "mahjongcraft.debug.stress.report.pending"

    /** 歷史遺失場次欄位。 */
    const val LOST: String = "mahjongcraft.debug.stress.report.lost"

    /** 歷史背景工作狀態欄位。 */
    const val WRITER: String = "mahjongcraft.debug.stress.report.writer"

    /** 歷史背景工作正常。 */
    const val WRITER_OK: String = "mahjongcraft.debug.stress.report.writer.ok"

    /** 歷史背景工作失敗。 */
    const val WRITER_FAILED: String = "mahjongcraft.debug.stress.report.writer.failed"

    /** 記憶體用量欄位。 */
    const val MEMORY: String = "mahjongcraft.debug.stress.report.memory"

    /** 記憶體回收次數與耗時欄位。 */
    const val GC: String = "mahjongcraft.debug.stress.report.gc"

    /** 每秒時間序列檔欄位。 */
    const val TIME_SERIES: String = "mahjongcraft.debug.stress.report.time_series"

    /** 停止原因欄位。 */
    const val STOP_REASON: String = "mahjongcraft.debug.stress.report.stop_reason"

    /** 爬坡模式最大可承受桌數欄位。 */
    const val SUSTAINED: String = "mahjongcraft.debug.stress.report.sustained"

    /** 停止原因：依要求停止。 */
    const val REASON_REQUESTED: String = "mahjongcraft.debug.stress.reason.requested"

    /** 停止原因：伺服器跟不上每秒 20 tick。 */
    const val REASON_FALLING_BEHIND: String = "mahjongcraft.debug.stress.reason.falling_behind"

    /** 停止原因：歷史待寫量達上限。 */
    const val REASON_HISTORY_BACKLOG: String = "mahjongcraft.debug.stress.reason.history_backlog"

    /** 停止原因：歷史遺失。 */
    const val REASON_HISTORY_LOST: String = "mahjongcraft.debug.stress.reason.history_lost"

    /** 停止原因：歷史背景工作失敗。 */
    const val REASON_HISTORY_WRITER_FAILED: String = "mahjongcraft.debug.stress.reason.history_writer_failed"

    /** 停止原因：伺服器關閉。 */
    const val REASON_SERVER_STOPPING: String = "mahjongcraft.debug.stress.reason.server_stopping"

    /** 停止原因：壓力測試意外失敗。 */
    const val REASON_RUN_FAILED: String = "mahjongcraft.debug.stress.reason.run_failed"
}
