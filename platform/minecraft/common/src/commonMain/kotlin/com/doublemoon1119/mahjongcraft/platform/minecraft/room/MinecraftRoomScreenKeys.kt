package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/** RoomScreen 與本地化設定呈現使用的 translation keys。 */
object MinecraftRoomScreenKeys {
    const val TITLE = "mahjongcraft.room.screen.title"
    const val PAGE_ROOM = "mahjongcraft.room.screen.page.room"
    const val PAGE_SETTINGS = "mahjongcraft.room.screen.page.settings"
    const val EMPTY = "mahjongcraft.room.screen.empty"
    const val PLAYING = "mahjongcraft.room.screen.playing"
    const val CREATE = "mahjongcraft.room.screen.create"
    const val JOIN = "mahjongcraft.room.screen.join"
    const val READY = "mahjongcraft.room.screen.ready"
    const val CANCEL_READY = "mahjongcraft.room.screen.cancel_ready"
    const val START = "mahjongcraft.room.screen.start"
    const val LEAVE = "mahjongcraft.room.screen.leave"
    const val DISBAND = "mahjongcraft.room.screen.disband"
    const val ADD_AI = "mahjongcraft.room.screen.add_ai"
    const val KICK = "mahjongcraft.room.screen.kick"
    const val APPLY = "mahjongcraft.room.screen.apply"
    const val CLOSE = "mahjongcraft.room.screen.close"
    const val DONE = "mahjongcraft.room.screen.done"
    const val UNDO = "mahjongcraft.room.screen.undo"
    const val HOST = "mahjongcraft.room.screen.host"
    const val AI = "mahjongcraft.room.screen.ai"
    const val AI_STRATEGY = "mahjongcraft.room.screen.ai_strategy"
    const val AI_STRATEGY_TITLE = "mahjongcraft.room.screen.ai_strategy_title"
    const val MEMBER_READY = "mahjongcraft.room.screen.member_ready"
    const val MEMBER_NOT_READY = "mahjongcraft.room.screen.member_not_ready"
    const val READ_ONLY = "mahjongcraft.room.screen.read_only"
    const val UNAVAILABLE = "mahjongcraft.room.screen.unavailable"
    const val DRAFT_STALE = "mahjongcraft.room.screen.draft_stale"
    const val VALIDATION_FAILED = "mahjongcraft.room.screen.validation_failed"
    const val RESET_DEFAULTS_BUTTON = "mahjongcraft.room.screen.reset_defaults"
    const val UNDO_DISCARD = "mahjongcraft.room.screen.tooltip.undo_discard"
    const val UNDO_RESTORE = "mahjongcraft.room.screen.tooltip.undo_restore"
    const val CURRENT_VALUE = "mahjongcraft.room.screen.tooltip.current"
    const val AVAILABLE_OPTIONS = "mahjongcraft.room.screen.tooltip.options"
    const val VALID_RANGE = "mahjongcraft.room.screen.tooltip.range"
    const val NORMAL_STEP = "mahjongcraft.room.screen.tooltip.step"
    const val SHIFT_STEP = "mahjongcraft.room.screen.tooltip.shift_step"
    const val KEYBOARD_INPUT = "mahjongcraft.room.screen.tooltip.keyboard_input"
    const val DISABLED_BY_DEPENDENCY = "mahjongcraft.room.screen.tooltip.disabled_by_dependency"
    const val RESET_DEFAULTS = "mahjongcraft.room.screen.tooltip.reset_defaults"
    const val RESET_NOT_SAVED = "mahjongcraft.room.screen.tooltip.reset_not_saved"
    const val NONE = "mahjongcraft.room.config.value.none"
    const val TRUE = "mahjongcraft.room.config.value.true"
    const val FALSE = "mahjongcraft.room.config.value.false"
    const val LOBBY_WAITING = "mahjongcraft.lobby.info.waiting"
    const val LOBBY_SUMMARY = "mahjongcraft.lobby.info.summary"
    const val LOBBY_VIEW_DETAILS = "mahjongcraft.lobby.info.view_details"

    /** 單選欄位選項的翻譯鍵前綴；實際 key 由 [configOption] 依去掉命名空間的選項 ID 組成。 */
    private const val CONFIG_OPTION_PREFIX = "mahjongcraft.room.config.option."

    /**
     * 取得單選欄位某個選項的翻譯鍵。
     *
     * @param optionId 帶命名空間的選項 ID（例如 `mahjongcraft:none`）；命名空間不進翻譯鍵。
     */
    fun configOption(optionId: String): String = CONFIG_OPTION_PREFIX + optionId.substringAfter(':')
}
