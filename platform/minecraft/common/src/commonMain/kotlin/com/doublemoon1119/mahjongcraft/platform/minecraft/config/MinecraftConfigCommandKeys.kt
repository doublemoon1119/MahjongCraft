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
