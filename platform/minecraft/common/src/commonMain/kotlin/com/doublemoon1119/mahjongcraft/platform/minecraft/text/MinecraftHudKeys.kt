package com.doublemoon1119.mahjongcraft.platform.minecraft.text

/** HUD 操作面板、操作卡與捨牌分析使用的翻譯鍵。 */
object MinecraftHudKeys {
    /** 吃牌操作卡短名稱。 */
    const val ACTION_CHI: String = "mahjongcraft.hud.action.chi"

    /** 碰牌操作卡短名稱。 */
    const val ACTION_PON: String = "mahjongcraft.hud.action.pon"

    /** 明槓操作卡短名稱。 */
    const val ACTION_KAN_OPEN: String = "mahjongcraft.hud.action.kan_open"

    /** 暗槓操作卡短名稱。 */
    const val ACTION_KAN_CLOSED: String = "mahjongcraft.hud.action.kan_closed"

    /** 加槓操作卡短名稱。 */
    const val ACTION_KAN_ADDED: String = "mahjongcraft.hud.action.kan_added"

    /** 榮和操作卡短名稱。 */
    const val ACTION_RON: String = "mahjongcraft.hud.action.ron"

    /** 自摸操作卡短名稱。 */
    const val ACTION_TSUMO: String = "mahjongcraft.hud.action.tsumo"

    /** 跳過操作卡短名稱。 */
    const val ACTION_PASS: String = "mahjongcraft.hud.action.pass"

    /** 打牌操作卡短名稱。 */
    const val ACTION_DISCARD: String = "mahjongcraft.hud.action.discard"

    /** 捨牌分析中「可以和牌」的狀態名稱。 */
    const val WIN_AVAILABILITY_AVAILABLE: String = "mahjongcraft.hud.win_availability.available"

    /** 選牌數量不足的提示。 */
    const val TILE_SELECTION_NEED_MORE: String = "mahjongcraft.hud.tile_selection_need_more"

    /** 選牌數量已足夠的提示。 */
    const val TILE_SELECTION_READY: String = "mahjongcraft.hud.tile_selection_ready"

    /** 確認操作卡短名稱。 */
    const val ACTION_CONFIRM: String = "mahjongcraft.hud.action.confirm"

    /** 選牌操作卡短名稱。 */
    const val ACTION_SELECT_TILES: String = "mahjongcraft.hud.action.select_tiles"

    /** 操作面板標題。 */
    const val ACTION_TITLE: String = "mahjongcraft.hud.action_title"

    /** 自己摸牌觸發操作時的說明。 */
    const val TRIGGER_SELF_DRAW: String = "mahjongcraft.hud.trigger.self_draw"

    /** 上家。 */
    const val RELATION_LEFT: String = "mahjongcraft.hud.relation.left"

    /** 對家。 */
    const val RELATION_ACROSS: String = "mahjongcraft.hud.relation.across"

    /** 下家。 */
    const val RELATION_RIGHT: String = "mahjongcraft.hud.relation.right"

    /** 他家動作觸發操作時的說明。 */
    const val TRIGGER: String = "mahjongcraft.hud.trigger"

    /** 待牌剩餘張數。 */
    const val REMAINING_TILES: String = "mahjongcraft.hud.remaining_tiles"

    /** 選牌進行中的提示。 */
    const val TILE_SELECTION_IN_PROGRESS: String = "mahjongcraft.hud.tile_selection_in_progress"

    /** 等待操作的提示。 */
    const val WAITING_FOR_ACTION: String = "mahjongcraft.hud.waiting_for_action"

    /** 重新開啟操作面板的提示。 */
    const val REOPEN_ACTION: String = "mahjongcraft.hud.reopen_action"

    /** 確認選牌的提示。 */
    const val TILE_SELECTION_CONFIRM: String = "mahjongcraft.hud.tile_selection_confirm"
}
