package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** Client Config Screen 使用的集中式翻譯鍵。 */
object MinecraftClientConfigScreenKeys {
    /** 畫面標題。 */
    const val TITLE: String = "mahjongcraft.client_config.title"

    /** 一般分類。 */
    const val CATEGORY_GENERAL: String = "mahjongcraft.client_config.category.general"

    /** 顯示分類。 */
    const val CATEGORY_DISPLAY: String = "mahjongcraft.client_config.category.display"

    /** 自動整理手牌欄位。 */
    const val AUTO_SORT_HAND: String = "mahjongcraft.client_config.auto_sort_hand"

    /** 自動整理手牌說明。 */
    const val AUTO_SORT_HAND_DESCRIPTION: String = "mahjongcraft.client_config.auto_sort_hand.description"

    /** 牌面輔助標籤欄位。 */
    const val TILE_LABELS: String = "mahjongcraft.client_config.tile_labels"

    /** 牌面輔助標籤說明。 */
    const val TILE_LABELS_DESCRIPTION: String = "mahjongcraft.client_config.tile_labels.description"

    /** HUD 位置編輯器入口。 */
    const val EDIT_HUD_LAYOUT: String = "mahjongcraft.client_config.edit_hud_layout"

    /** HUD 位置編輯器入口說明。 */
    const val EDIT_HUD_LAYOUT_DESCRIPTION: String = "mahjongcraft.client_config.edit_hud_layout.description"

    /** HUD 位置編輯器標題。 */
    const val HUD_LAYOUT_TITLE: String = "mahjongcraft.hud_layout.title"

    /** 操作面板預覽名稱。 */
    const val HUD_LAYOUT_DECISION_PANEL: String = "mahjongcraft.hud_layout.decision_panel"

    /** 一般倒數與等待提醒預覽名稱。 */
    const val HUD_LAYOUT_COMPACT_PROMPT: String = "mahjongcraft.hud_layout.compact_prompt"

    /** 打牌分析預覽名稱。 */
    const val HUD_LAYOUT_DISCARD_ANALYSIS: String = "mahjongcraft.hud_layout.discard_analysis"

    /** 重設 HUD 配置按鈕。 */
    const val HUD_LAYOUT_RESET: String = "mahjongcraft.hud_layout.reset"

    /** 返回按鈕。 */
    const val BACK: String = "mahjongcraft.client_config.back"

    /** 未套用 HUD 配置確認標題。 */
    const val HUD_LAYOUT_UNSAVED_TITLE: String = "mahjongcraft.hud_layout.unsaved.title"

    /** 未套用 HUD 配置確認說明。 */
    const val HUD_LAYOUT_UNSAVED_MESSAGE: String = "mahjongcraft.hud_layout.unsaved.message"

    /** 套用並返回按鈕。 */
    const val APPLY_AND_BACK: String = "mahjongcraft.hud_layout.apply_and_back"

    /** 放棄變更按鈕。 */
    const val DISCARD_CHANGES: String = "mahjongcraft.hud_layout.discard_changes"

    /** 繼續編輯按鈕。 */
    const val CONTINUE_EDITING: String = "mahjongcraft.hud_layout.continue_editing"

    /** HUD 百分比位置的 tooltip 標題。 */
    const val HUD_LAYOUT_CHANGES: String = "mahjongcraft.hud_layout.changes"

    /** 單一 X、Y HUD 位置格式。 */
    const val HUD_LAYOUT_XY_CHANGE: String = "mahjongcraft.hud_layout.change.xy"

    /** 單一 Y 軸 HUD 位置格式。 */
    const val HUD_LAYOUT_Y_CHANGE: String = "mahjongcraft.hud_layout.change.y"

    /** Client 設定差異 tooltip 標題。 */
    const val CONFIG_CHANGES: String = "mahjongcraft.client_config.changes"

    /** Client 設定差異列格式。 */
    const val CONFIG_VALUE_CHANGE: String = "mahjongcraft.client_config.change.value"

    /** 鳴牌預覽情境。 */
    const val HUD_LAYOUT_SCENARIO_CALL: String = "mahjongcraft.hud_layout.scenario.call"

    /** 立直預覽情境。 */
    const val HUD_LAYOUT_SCENARIO_RIICHI: String = "mahjongcraft.hud_layout.scenario.riichi"

    /** 九種九牌預覽情境。 */
    const val HUD_LAYOUT_SCENARIO_ABORTIVE_DRAW: String = "mahjongcraft.hud_layout.scenario.abortive_draw"

    /** HUD 選擇下拉欄位格式。 */
    const val HUD_LAYOUT_SELECTOR_HUD: String = "mahjongcraft.hud_layout.selector.hud"

    /** 預覽模式下拉欄位格式。 */
    const val HUD_LAYOUT_SELECTOR_VISIBILITY: String = "mahjongcraft.hud_layout.selector.visibility"

    /** 操作情境下拉欄位格式。 */
    const val HUD_LAYOUT_SELECTOR_SCENARIO: String = "mahjongcraft.hud_layout.selector.scenario"

    /** 隱藏 editor 控制項按鈕。 */
    const val HUD_LAYOUT_HIDE_CONTROLS: String = "mahjongcraft.hud_layout.hide_controls"

    /** 恢復 editor 控制項提示。 */
    const val HUD_LAYOUT_SHOW_CONTROLS_HINT: String = "mahjongcraft.hud_layout.show_controls_hint"

    /** 只顯示非作用中 HUD 外框。 */
    const val HUD_LAYOUT_VISIBILITY_OUTLINE: String = "mahjongcraft.hud_layout.visibility.outline"

    /** 隱藏非作用中 HUD 預覽。 */
    const val HUD_LAYOUT_VISIBILITY_HIDDEN: String = "mahjongcraft.hud_layout.visibility.hidden"

    /** Boolean 開啟文字。 */
    const val ENABLED: String = "mahjongcraft.client_config.enabled"

    /** Boolean 關閉文字。 */
    const val DISABLED: String = "mahjongcraft.client_config.disabled"

    /** 恢復程式預設值按鈕。 */
    const val RESET_DEFAULTS: String = "mahjongcraft.client_config.reset_defaults"

    /** 復原未套用變更按鈕。 */
    const val UNDO: String = "mahjongcraft.client_config.undo"

    /** 套用草稿按鈕。 */
    const val APPLY: String = "mahjongcraft.client_config.apply"

    /** 套用並離開按鈕。 */
    const val DONE: String = "mahjongcraft.client_config.done"

    /** 保存失敗狀態。 */
    const val SAVE_FAILED: String = "mahjongcraft.client_config.save_failed"

    /** 草稿因外部設定變更而過期。 */
    const val DRAFT_STALE: String = "mahjongcraft.client_config.draft_stale"

    /** 開啟設定畫面的按鍵名稱。 */
    const val OPEN_KEY: String = "key.mahjongcraft.open_client_config"

    /** MahjongCraft 按鍵分類。 */
    const val KEY_CATEGORY: String = "key.category.mahjongcraft"
}
