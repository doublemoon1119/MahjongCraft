package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** Server／client 設定指令與設定呈現共用的翻譯鍵。 */
object MinecraftConfigCommandKeys {
    /** Client 設定的顯示名稱。 */
    const val CLIENT_CONFIG: String = "mahjongcraft.config.client"

    /** Server 設定的顯示名稱。 */
    const val SERVER_CONFIG: String = "mahjongcraft.config.server"

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
