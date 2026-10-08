package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** 壓力測試 debug 指令共用的本地化翻譯鍵。 */
object StressDebugKeys {
    /** 翻譯鍵共同前綴。 */
    private const val PREFIX: String = "mahjongcraft.debug.stress."

    /** 已開始壓力測試。 */
    const val STARTED: String = PREFIX + "started"

    /** 在單人世界的內建伺服器上開始時，提醒數據僅供參考。 */
    const val INTEGRATED_SERVER_HINT: String = PREFIX + "integrated_server_hint"

    /** 已有壓力測試執行中。 */
    const val BUSY: String = PREFIX + "busy"

    /** 對局情境不存在。 */
    const val INVALID_SCENARIO: String = PREFIX + "invalid_scenario"

    /** 推進節奏不存在。 */
    const val INVALID_PACE: String = PREFIX + "invalid_pace"

    /** 歷史處理方式不存在。 */
    const val INVALID_HISTORY_MODE: String = PREFIX + "invalid_history_mode"

    /** 不認得的選項，參數為寫錯的名稱與可用的選項。 */
    const val UNKNOWN_OPTION: String = PREFIX + "unknown_option"

    /** 選項缺少值，參數為選項名稱。 */
    const val MISSING_OPTION_VALUE: String = PREFIX + "missing_option_value"

    /** 選項重複，參數為選項名稱。 */
    const val DUPLICATE_OPTION: String = PREFIX + "duplicate_option"

    /** 數值選項超出範圍，參數為選項名稱、最小值與最大值。 */
    const val INVALID_OPTION_NUMBER: String = PREFIX + "invalid_option_number"

    /** 壓力測試資料庫無法開啟。 */
    const val STORAGE_UNAVAILABLE: String = PREFIX + "storage_unavailable"

    /** 沒有執行中的壓力測試。 */
    const val NOT_RUNNING: String = PREFIX + "not_running"

    /** 還沒有任何壓力測試報告。 */
    const val NO_REPORT: String = PREFIX + "no_report"

    /** 已刪除壓力測試資料庫。 */
    const val CLEARED: String = PREFIX + "cleared"

    /** 沒有壓力測試資料庫可刪除。 */
    const val NOTHING_TO_CLEAR: String = PREFIX + "nothing_to_clear"

    /** 壓力測試仍在執行，不能刪除資料庫。 */
    const val CLEAR_WHILE_RUNNING: String = PREFIX + "clear_while_running"

    /** 執行中報告的標題。 */
    const val TITLE_RUNNING: String = PREFIX + "report.title_running"

    /** 已停止報告的標題。 */
    const val TITLE_STOPPED: String = PREFIX + "report.title_stopped"

    /** 對局情境欄位。 */
    const val SCENARIO: String = PREFIX + "report.scenario"

    /** 桌數安排欄位。 */
    const val MODE: String = PREFIX + "report.mode"

    /** 固定桌數的安排，參數為桌數。 */
    const val MODE_FIXED: String = PREFIX + "report.mode.fixed"

    /** 爬坡的安排。 */
    const val MODE_RAMP: String = PREFIX + "report.mode.ramp"

    /** 推進節奏欄位。 */
    const val PACE: String = PREFIX + "report.pace"

    /** 真實節奏。 */
    const val PACE_REALTIME: String = PREFIX + "report.pace.realtime"

    /** 全速節奏。 */
    const val PACE_FAST: String = PREFIX + "report.pace.fast"

    /** 歷史處理方式欄位。 */
    const val HISTORY_MODE: String = PREFIX + "report.history_mode"

    /** 歷史處理方式翻譯鍵前綴，後接指令中的名稱。 */
    const val HISTORY_MODE_PREFIX: String = PREFIX + "report.history_mode."

    /** 同時進行桌數欄位。 */
    const val TABLES: String = PREFIX + "report.tables"

    /** 已完成場數欄位。 */
    const val COMPLETED: String = PREFIX + "report.completed"

    /** 卡住中止場數欄位。 */
    const val FAILED: String = PREFIX + "report.failed"

    /** 經過時間欄位。 */
    const val ELAPSED: String = PREFIX + "report.elapsed"

    /** 暖機狀態欄位。 */
    const val WARMUP: String = PREFIX + "report.warmup"

    /** 暖機中，參數為剩餘秒數。 */
    const val WARMUP_RUNNING: String = PREFIX + "report.warmup.running"

    /** 暖機已結束，參數為排除的秒數。 */
    const val WARMUP_DONE: String = PREFIX + "report.warmup.done"

    /** 這次測試沒有暖機。 */
    const val WARMUP_NONE: String = PREFIX + "report.warmup.none"

    /** 每 tick 耗時欄位。 */
    const val TICK: String = PREFIX + "report.tick"

    /** 每 tick 耗時超過各門檻的比例欄位。 */
    const val SLOW_TICKS: String = PREFIX + "report.slow_ticks"

    /** 開始持續卡頓時的桌數欄位，參數為卡頓的毫秒門檻。 */
    const val STUTTER: String = PREFIX + "report.stutter"

    /** 尚未發生持續卡頓。 */
    const val STUTTER_NONE: String = PREFIX + "report.stutter.none"

    /** 單步耗時欄位。 */
    const val STEP: String = PREFIX + "report.step"

    /** 單步耗時拆解欄位。 */
    const val STEP_BREAKDOWN: String = PREFIX + "report.step_breakdown"

    /** 單步環節翻譯鍵前綴，後接環節名稱的小寫。 */
    const val STEP_STAGE_PREFIX: String = PREFIX + "report.step_stage."

    /** 單一 tick 內最多步數與歷史事件數欄位。 */
    const val PEAK_PER_TICK: String = PREFIX + "report.peak_per_tick"

    /** 歷史事件產生與處理數量欄位。 */
    const val EVENTS: String = PREFIX + "report.events"

    /** 歷史背景工作耗時拆解欄位。 */
    const val WRITER_BREAKDOWN: String = PREFIX + "report.writer_breakdown"

    /** 歷史背景工作環節翻譯鍵前綴，後接環節名稱的小寫。 */
    const val WRITER_STAGE_PREFIX: String = PREFIX + "report.writer_stage."

    /** 歷史待寫佇列欄位。 */
    const val PENDING: String = PREFIX + "report.pending"

    /** 歷史遺失場次欄位。 */
    const val LOST: String = PREFIX + "report.lost"

    /** 歷史背景工作狀態欄位。 */
    const val WRITER: String = PREFIX + "report.writer"

    /** 歷史背景工作正常。 */
    const val WRITER_OK: String = PREFIX + "report.writer.ok"

    /** 歷史背景工作失敗。 */
    const val WRITER_FAILED: String = PREFIX + "report.writer.failed"

    /** 記憶體用量欄位。 */
    const val MEMORY: String = PREFIX + "report.memory"

    /** 記憶體回收次數與耗時欄位。 */
    const val GC: String = PREFIX + "report.gc"

    /** 每秒時間序列檔欄位。 */
    const val TIME_SERIES: String = PREFIX + "report.time_series"

    /** 停止原因欄位。 */
    const val STOP_REASON: String = PREFIX + "report.stop_reason"

    /** 爬坡模式最大可承受桌數欄位。 */
    const val SUSTAINED: String = PREFIX + "report.sustained"

    /** 停止原因翻譯鍵前綴，後接停止原因名稱的小寫。 */
    const val REASON_PREFIX: String = PREFIX + "reason."
}
