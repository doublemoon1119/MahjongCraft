package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** Server／client 設定指令與設定呈現共用的翻譯鍵。 */
object MinecraftConfigCommandKeys {
    /** Client 設定的顯示名稱。 */
    const val CLIENT_CONFIG: String = "mahjongcraft.config.client"

    /** Server 設定的顯示名稱。 */
    const val SERVER_CONFIG: String = "mahjongcraft.config.server"

    /** 斷線玩家設定區段的顯示名稱。 */
    const val SERVER_CONFIG_SECTION_PLAYER_DISCONNECTION: String = "mahjongcraft.config.section.player_disconnection"

    /** 麻將桌設定區段的顯示名稱。 */
    const val SERVER_CONFIG_SECTION_TABLE: String = "mahjongcraft.config.section.table"

    /** 麻將牌設定區段的顯示名稱。 */
    const val SERVER_CONFIG_SECTION_MAHJONG_TILE: String = "mahjongcraft.config.section.mahjong_tile"

    /** 對局歷史設定區段的顯示名稱。 */
    const val SERVER_CONFIG_SECTION_HISTORY: String = "mahjongcraft.config.section.history"

    /** 設定頁面的顯示名稱。 */
    const val SETTINGS: String = "mahjongcraft.config.settings"

    /** 設定檔位置的顯示名稱。 */
    const val FILE_LOCATION: String = "mahjongcraft.config.file_location"

    /** 重新載入成功，帶設定名稱參數。 */
    const val RELOADED: String = "mahjongcraft.config.command.reloaded"

    /** 重新載入失敗，帶設定名稱與詳情標籤參數。 */
    const val RELOAD_FAILED: String = "mahjongcraft.config.command.reload_failed"

    /** 保存設定失敗，帶欄位名稱與詳情標籤參數。 */
    const val SAVE_FAILED: String = "mahjongcraft.config.command.save_failed"

    /** 顯示目前設定，帶設定名稱與詳情標籤參數。 */
    const val CURRENT: String = "mahjongcraft.config.command.current"

    /** 可懸停查看內容的詳情標籤。 */
    const val DETAILS: String = "mahjongcraft.config.command.details"

    /** 設定檔位置，帶路徑參數。 */
    const val PATH: String = "mahjongcraft.config.command.path"

    /** 歷史狀態的單行訊息，帶可懸停的詳情標籤。 */
    const val HISTORY_STATUS: String = "mahjongcraft.history.command.status"

    /** 歷史狀態懸停內容的標題。 */
    const val HISTORY_TITLE: String = "mahjongcraft.history.command.title"

    /** 資料庫連線狀態欄位。 */
    const val HISTORY_DATABASE: String = "mahjongcraft.history.command.database"

    /** 歷史事件記錄狀態欄位。 */
    const val HISTORY_RECORDING: String = "mahjongcraft.history.command.recording"

    /** 權威 outbox 待寫事件數欄位。 */
    const val HISTORY_PENDING_EVENTS: String = "mahjongcraft.history.command.pending_events"

    /** 已知序號缺口的場次數欄位。 */
    const val HISTORY_KNOWN_GAPS: String = "mahjongcraft.history.command.known_gaps"

    /** 最近錯誤狀態欄位。 */
    const val HISTORY_ERROR: String = "mahjongcraft.history.command.error"

    /** 歷史資料庫重新連結成功訊息。 */
    const val HISTORY_RECONNECTED: String = "mahjongcraft.history.command.reconnected"

    /** 歷史資料庫已經連線訊息。 */
    const val HISTORY_ALREADY_CONNECTED: String = "mahjongcraft.history.command.already_connected"

    /** 歷史資料庫重新連結失敗訊息。 */
    const val HISTORY_RECONNECT_FAILED: String = "mahjongcraft.history.command.reconnect_failed"

    /** 歷史資料庫目前已連線狀態文字。 */
    const val HISTORY_CONNECTED: String = "mahjongcraft.history.command.connected"

    /** 歷史資料庫目前未連線狀態文字。 */
    const val HISTORY_DISCONNECTED: String = "mahjongcraft.history.command.disconnected"

    /** 歷史事件記錄目前啟用狀態文字。 */
    const val HISTORY_RECORDING_ENABLED: String = "mahjongcraft.history.command.recording_enabled"

    /** 歷史事件記錄目前停用狀態文字。 */
    const val HISTORY_RECORDING_DISABLED: String = "mahjongcraft.history.command.recording_disabled"

    /** 歷史狀態有需查看伺服器 log 的錯誤時，避免公開原始例外內容。 */
    const val HISTORY_ERROR_PRESENT: String = "mahjongcraft.history.command.error_present"

    /** 未偵測到錯誤時的安全摘要。 */
    const val HISTORY_ERROR_NONE: String = "mahjongcraft.history.command.error_none"

    /** 歷史資料容量狀態欄位。 */
    const val HISTORY_STORAGE_STATE: String = "mahjongcraft.command.history.storage_state"

    /** 歷史資料暫停新增狀態文字。 */
    const val HISTORY_STORAGE_PAUSED: String = "mahjongcraft.command.history.storage_paused"

    /** 歷史資料可正常記錄狀態文字。 */
    const val HISTORY_STORAGE_AVAILABLE: String = "mahjongcraft.command.history.storage_available"

    /** 歷史用量快照標題。 */
    const val HISTORY_STORAGE: String = "mahjongcraft.history.command.storage"

    /** 對局分類標籤。 */
    const val HISTORY_SECTION_MATCHES: String = "mahjongcraft.history.command.section.matches"

    /** 事件分類標籤。 */
    const val HISTORY_SECTION_EVENTS: String = "mahjongcraft.history.command.section.events"

    /** 磁碟分類標籤。 */
    const val HISTORY_SECTION_DISK: String = "mahjongcraft.history.command.section.disk"

    /** 有效保留限制分類標籤。 */
    const val HISTORY_SECTION_LIMITS: String = "mahjongcraft.history.command.section.limits"

    /** 完整、活動、部分及待判定場數摘要。 */
    const val HISTORY_MATCHES_SUMMARY: String = "mahjongcraft.history.command.matches_summary"

    /** SQL、權威 outbox 與清理收據摘要。 */
    const val HISTORY_EVENTS_SUMMARY: String = "mahjongcraft.history.command.events_summary"

    /** 場數、天數及磁碟上限摘要。 */
    const val HISTORY_LIMITS_SUMMARY: String = "mahjongcraft.history.command.limits_summary"

    /** 無場數或天數上限文字。 */
    const val HISTORY_UNLIMITED: String = "mahjongcraft.history.command.unlimited"

    /** 主資料庫檔案大小。 */
    const val HISTORY_DB_BYTES: String = "mahjongcraft.history.command.db_bytes"

    /** WAL 檔案大小。 */
    const val HISTORY_WAL_BYTES: String = "mahjongcraft.history.command.wal_bytes"

    /** SHM 檔案大小。 */
    const val HISTORY_SHM_BYTES: String = "mahjongcraft.history.command.shm_bytes"

    /** 預覽中確定按政策清理的候選場數。 */
    const val HISTORY_CANDIDATE_MATCHES: String = "mahjongcraft.history.command.candidate_matches"

    /** 清理是否完成的欄位名稱。 */
    const val HISTORY_RESULT_STATE: String = "mahjongcraft.history.command.result_state"

    /** 肯定結果文字。 */
    const val HISTORY_YES: String = "mahjongcraft.history.command.yes"

    /** 否定結果文字。 */
    const val HISTORY_NO: String = "mahjongcraft.history.command.no"

    /** 未量得或未確認的結果文字。 */
    const val HISTORY_UNKNOWN: String = "mahjongcraft.history.command.unknown"

    /** 歷史管理工作進行中訊息。 */
    const val HISTORY_MANAGEMENT_BUSY: String = "mahjongcraft.history.command.management_busy"

    /** 歷史資料庫尚未連線訊息。 */
    const val HISTORY_MANAGEMENT_DISCONNECTED: String = "mahjongcraft.history.command.management_disconnected"

    /** 歷史管理工作失敗訊息。 */
    const val HISTORY_MANAGEMENT_FAILED: String = "mahjongcraft.history.command.management_failed"

    /** 開始讀取歷史用量訊息。 */
    const val HISTORY_STORAGE_STARTED: String = "mahjongcraft.history.command.storage_started"

    /** 開始產生清理預覽訊息。 */
    const val HISTORY_PREVIEW_STARTED: String = "mahjongcraft.history.command.preview_started"

    /** 開始執行清理訊息。 */
    const val HISTORY_CLEANUP_STARTED: String = "mahjongcraft.history.command.cleanup_started"

    /** 歷史用量快照的詳細欄位。 */
    /** 已完成場次數量。 */
    const val HISTORY_COMPLETED_MATCHES: String = "mahjongcraft.history.command.completed_matches"

    /** 活動場次數量。 */
    const val HISTORY_ACTIVE_MATCHES: String = "mahjongcraft.history.command.active_matches"

    /** 部分場次數量。 */
    const val HISTORY_PARTIAL_MATCHES: String = "mahjongcraft.history.command.partial_matches"

    /** 未知分類場次數量。 */
    const val HISTORY_UNKNOWN_MATCHES: String = "mahjongcraft.history.command.unknown_matches"

    /** SQLite 待封存事件數量。 */
    const val HISTORY_PENDING_SQL_EVENTS: String = "mahjongcraft.history.command.pending_sql_events"

    /** 權威 outbox 待寫事件數量。 */
    const val HISTORY_PENDING_OUTBOX_EVENTS: String = "mahjongcraft.history.command.pending_outbox_events"

    /** 清理墓碑數量。 */
    const val HISTORY_TOMBSTONES: String = "mahjongcraft.history.command.tombstones"

    /** 歷史資料庫磁碟用量。 */
    const val HISTORY_DISK_USAGE: String = "mahjongcraft.history.command.disk_usage"

    /** 快照更新時間。 */
    const val HISTORY_UPDATED_AT: String = "mahjongcraft.history.command.updated_at"

    /** 快照是否已過期。 */
    const val HISTORY_STALE: String = "mahjongcraft.history.command.stale"

    /** 清理預覽標題與候選統計欄位。 */
    /** 清理預覽標題。 */
    const val HISTORY_CLEANUP_PREVIEW: String = "mahjongcraft.history.command.cleanup_preview"

    /** 清理預覽標題文字。 */
    const val HISTORY_CLEANUP_PREVIEW_TITLE: String = "mahjongcraft.history.command.cleanup_preview_title"

    /** 清理報告標題文字。 */
    const val HISTORY_CLEANUP_REPORT_TITLE: String = "mahjongcraft.history.command.cleanup_report_title"

    /** 清理結果完成狀態文字。 */
    const val HISTORY_RESULT_COMPLETED: String = "mahjongcraft.history.command.result_completed"

    /** 清理結果部分狀態文字。 */
    const val HISTORY_RESULT_PARTIAL: String = "mahjongcraft.history.command.result_partial"

    /** 清理原因欄位。 */
    const val HISTORY_CLEANUP_REASON: String = "mahjongcraft.history.command.cleanup_reason"

    /** 候選資料邏輯大小欄位。 */
    const val HISTORY_LOGICAL_BYTES: String = "mahjongcraft.history.command.logical_bytes"

    /** 額外候選場次數量。 */
    const val HISTORY_ADDITIONAL_CANDIDATES: String = "mahjongcraft.history.command.additional_candidates"

    /** 額外候選邏輯大小。 */
    const val HISTORY_ADDITIONAL_LOGICAL_BYTES: String = "mahjongcraft.history.command.additional_logical_bytes"

    /** 候選評估時間。 */
    const val HISTORY_EVALUATED_AT: String = "mahjongcraft.history.command.evaluated_at"

    /** 無法精確承諾磁碟釋放量的說明。 */
    const val HISTORY_NO_EXACT_DISK_PROMISE: String = "mahjongcraft.history.command.no_exact_disk_promise"

    /** 清理執行報告欄位。 */
    /** 清理是否完成。 */
    const val HISTORY_CLEANUP_COMPLETE: String = "mahjongcraft.history.command.cleanup_complete"

    /** 已移除場次數量。 */
    const val HISTORY_REMOVED_MATCHES: String = "mahjongcraft.history.command.removed_matches"

    /** 清理前磁碟大小。 */
    const val HISTORY_DISK_BEFORE: String = "mahjongcraft.history.command.disk_before"

    /** 清理後磁碟大小。 */
    const val HISTORY_DISK_AFTER: String = "mahjongcraft.history.command.disk_after"

    /** 磁碟大小淨變化。 */
    const val HISTORY_NET_DISK_CHANGE: String = "mahjongcraft.history.command.net_disk_change"

    /** 磁碟回收是否因忙碌而延後。 */
    const val HISTORY_RECOVERY_BUSY: String = "mahjongcraft.history.command.recovery_busy"

    /** 是否支援 incremental vacuum。 */
    const val HISTORY_INCREMENTAL_SUPPORTED: String = "mahjongcraft.history.command.incremental_supported"

    /** 歷史儲存可用結果文字。 */
    const val HISTORY_STORAGE_AVAILABLE_RESULT: String = "mahjongcraft.history.command.storage_available_result"

    /** 清理原因的本地化分類。 */
    const val HISTORY_REASON_INTERRUPTED: String = "mahjongcraft.history.command.reason.interrupted"
    const val HISTORY_REASON_EXPIRED: String = "mahjongcraft.history.command.reason.expired"
    const val HISTORY_REASON_MATCH_LIMIT: String = "mahjongcraft.history.command.reason.match_limit"
    const val HISTORY_REASON_DISK_LIMIT: String = "mahjongcraft.history.command.reason.disk_limit"

    /** 斷線玩家政策欄位。 */
    const val DISCONNECTED_PLAYER_POLICY: String = "mahjongcraft.server_config.disconnected_player_policy"

    /** 斷線玩家逾時欄位。 */
    const val DISCONNECTED_PLAYER_TIMEOUT: String = "mahjongcraft.server_config.disconnected_player_timeout"

    /** 麻將桌破壞政策欄位。 */
    const val TABLE_BREAK_POLICY: String = "mahjongcraft.server_config.table_break_policy"

    /** 缺失麻將桌政策欄位。 */
    const val ORPHANED_TABLE_POLICY: String = "mahjongcraft.server_config.orphaned_table_policy"

    /** 麻將牌實體碰撞欄位。 */
    const val TILE_COLLISION: String = "mahjongcraft.server_config.tile_collision"

    /** 歷史資料是否啟用欄位。 */
    const val HISTORY_ENABLED: String = "mahjongcraft.server_config.history_enabled"

    /** 是否記錄 AI 牌局欄位。 */
    const val HISTORY_INCLUDE_AI_MATCHES: String = "mahjongcraft.server_config.history_include_ai_matches"

    /** 是否記錄中斷牌局欄位。 */
    const val HISTORY_INCLUDE_INTERRUPTED_MATCHES: String = "mahjongcraft.server_config.history_include_interrupted_matches"

    /** 是否允許歷史查詢欄位。 */
    const val HISTORY_QUERY_ENABLED: String = "mahjongcraft.server_config.history_query_enabled"

    /** 是否允許管理員查詢全部歷史欄位。 */
    const val HISTORY_ALLOW_ADMIN_QUERY: String = "mahjongcraft.server_config.history_allow_admin_query"

    /** 歷史查詢最短間隔欄位。 */
    const val HISTORY_QUERY_MINIMUM_INTERVAL: String = "mahjongcraft.server_config.history_query_minimum_interval"

    /** 全伺服器歷史查詢工作上限欄位。 */
    const val HISTORY_QUERY_MAX_OUTSTANDING: String = "mahjongcraft.server_config.history_query_max_outstanding"

    /** 歷史查詢拒絕回覆間隔欄位。 */
    const val HISTORY_QUERY_REJECTION_REPLY_INTERVAL: String =
        "mahjongcraft.server_config.history_query_rejection_reply_interval"

    /** 歷史資料最多保留牌局數量欄位。 */
    const val HISTORY_MAX_MATCHES: String = "mahjongcraft.server_config.history_max_matches"

    /** 歷史資料保留天數欄位。 */
    const val HISTORY_RETENTION_DAYS: String = "mahjongcraft.server_config.history_retention_days"

    /** 歷史資料磁碟空間上限欄位。 */
    const val HISTORY_MAX_DISK_MIB: String = "mahjongcraft.server_config.history_max_disk_mib"

    /** 保留斷線玩家座位選項。 */
    const val KEEP_SEAT: String = "mahjongcraft.server_config.option.keep_seat"

    /** 斷線時立即離開選項。 */
    const val LEAVE_IMMEDIATELY: String = "mahjongcraft.server_config.option.leave_immediately"

    /** 斷線逾時後離開選項。 */
    const val LEAVE_AFTER_TIMEOUT: String = "mahjongcraft.server_config.option.leave_after_timeout"

    /** 有使用中桌子時拒絕破壞選項。 */
    const val DENY_WHILE_OCCUPIED: String = "mahjongcraft.server_config.option.deny_while_occupied"

    /** 只允許破壞等待中桌子選項。 */
    const val ALLOW_WAITING_ROOM_ONLY: String = "mahjongcraft.server_config.option.allow_waiting_game_only"

    /** 允許破壞並終止遊戲選項。 */
    const val ALLOW_AND_TERMINATE: String = "mahjongcraft.server_config.option.allow_and_terminate"

    /** 保留缺失桌子資料並警告選項。 */
    const val KEEP_AND_WARN: String = "mahjongcraft.server_config.option.keep_and_warn"

    /** 移除缺失桌子的等待中遊戲選項。 */
    const val REMOVE_WAITING_ROOM: String = "mahjongcraft.server_config.option.remove_waiting_game"

    /** 移除缺失桌子的所有資料選項。 */
    const val REMOVE_ALL: String = "mahjongcraft.server_config.option.remove_all"
}
