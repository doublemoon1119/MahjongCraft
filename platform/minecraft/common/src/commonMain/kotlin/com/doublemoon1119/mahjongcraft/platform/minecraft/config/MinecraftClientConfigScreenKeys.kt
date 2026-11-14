package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** Client Config Screen 使用的集中式翻譯鍵。 */
object MinecraftClientConfigScreenKeys {
    /** 畫面標題。 */
    const val TITLE: String = "mahjongcraft.client_config.title"

    /** 自動操作分類。 */
    const val CATEGORY_AUTOMATIC: String = "mahjongcraft.client_config.category.automatic"

    /** 本局控制與永久偏好的分隔說明。 */
    const val AUTOMATIC_ROUND_ONLY: String = "mahjongcraft.client_config.automatic.round_only"

    /** 不在對局時的說明。 */
    const val AUTOMATIC_UNAVAILABLE: String = "mahjongcraft.client_config.automatic.unavailable"

    /** 目前規則未提供本局控制時的說明。 */
    const val AUTOMATIC_EMPTY: String = "mahjongcraft.client_config.automatic.empty"

    /** 等待伺服器確認。 */
    const val AUTOMATIC_PENDING: String = "mahjongcraft.client_config.automatic.pending"

    /** 本局控制草稿因權威狀態更新而過期。 */
    const val AUTOMATIC_STALE: String = "mahjongcraft.client_config.automatic.stale"

    /** 等不到伺服器確認，正在重新同步。 */
    const val AUTOMATIC_RESYNCING: String = "mahjongcraft.client_config.automatic.resyncing"

    /** 重新同步確認更新沒有生效，可以再套用一次。 */
    const val AUTOMATIC_NOT_APPLIED: String = "mahjongcraft.client_config.automatic.not_applied"

    /** 無法確認更新是否生效，需要復原載入最新狀態。 */
    const val AUTOMATIC_UNCONFIRMED: String = "mahjongcraft.client_config.automatic.unconfirmed"

    /** HUD 類別。 */
    const val CATEGORY_HUD: String = "mahjongcraft.client_config.category.hud"

    /** 遊戲面板類別。 */
    const val CATEGORY_GAME_PANELS: String = "mahjongcraft.client_config.category.game_panels"

    /** 視覺提示類別。 */
    const val CATEGORY_VISUAL_FEEDBACK: String = "mahjongcraft.client_config.category.visual_feedback"

    /** 開啟客戶端設定的快捷鍵。 */
    const val OPEN_CLIENT_CONFIG_KEY: String = "key.mahjongcraft.open_client_config"

    /** 開啟對局歷史的快捷鍵。 */
    const val OPEN_HISTORY_KEY: String = "key.mahjongcraft.open_history"

    /** 開啟規則一覽的快捷鍵。 */
    const val OPEN_RULE_CATALOGUE_KEY: String = "key.mahjongcraft.open_rule_catalogue"

    /** 自動整理手牌欄位。 */
    const val AUTO_SORT_HAND: String = "mahjongcraft.client_config.auto_sort_hand"

    /** 自動整理手牌說明。 */
    const val AUTO_SORT_HAND_DESCRIPTION: String = "mahjongcraft.client_config.auto_sort_hand.description"

    /** 牌面輔助標籤欄位。 */
    const val TILE_LABELS: String = "mahjongcraft.client_config.tile_labels"

    /** 牌面輔助標籤說明。 */
    const val TILE_LABELS_DESCRIPTION: String = "mahjongcraft.client_config.tile_labels.description"

    /** 顯示開關：局況資訊。 */
    const val PRESENTATION_ROUND_INFO: String = "mahjongcraft.client_config.presentation.round_info"

    /** 顯示開關說明：局況資訊。 */
    const val PRESENTATION_ROUND_INFO_DESCRIPTION: String = "mahjongcraft.client_config.presentation.round_info.description"

    /** 顯示開關：玩家資訊。 */
    const val PRESENTATION_PLAYER_INFO: String = "mahjongcraft.client_config.presentation.player_info"

    /** 顯示開關說明：玩家資訊。 */
    const val PRESENTATION_PLAYER_INFO_DESCRIPTION: String = "mahjongcraft.client_config.presentation.player_info.description"

    /** 顯示開關：大廳資訊。 */
    const val PRESENTATION_LOBBY_INFO: String = "mahjongcraft.client_config.presentation.lobby_info"

    /** 顯示開關說明：大廳資訊。 */
    const val PRESENTATION_LOBBY_INFO_DESCRIPTION: String = "mahjongcraft.client_config.presentation.lobby_info.description"

    /** 顯示開關：骰子結果。 */
    const val PRESENTATION_DICE_RESULT: String = "mahjongcraft.client_config.presentation.dice_result"

    /** 顯示開關說明：骰子結果。 */
    const val PRESENTATION_DICE_RESULT_DESCRIPTION: String = "mahjongcraft.client_config.presentation.dice_result.description"

    /** 顯示開關：精簡提示。 */
    const val PRESENTATION_COMPACT_PROMPT: String = "mahjongcraft.client_config.presentation.compact_prompt"

    /** 顯示開關說明：精簡提示。 */
    const val PRESENTATION_COMPACT_PROMPT_DESCRIPTION: String = "mahjongcraft.client_config.presentation.compact_prompt.description"

    /** 顯示開關：捨牌分析。 */
    const val PRESENTATION_DISCARD_ANALYSIS: String = "mahjongcraft.client_config.presentation.discard_analysis"

    /** 顯示開關說明：捨牌分析。 */
    const val PRESENTATION_DISCARD_ANALYSIS_DESCRIPTION: String = "mahjongcraft.client_config.presentation.discard_analysis.description"

    /** 顯示開關：自動操作狀態。 */
    const val PRESENTATION_AUTOMATIC_CONTROL_STATUS: String = "mahjongcraft.client_config.presentation.automatic_control_status"

    /** 顯示開關說明：自動操作狀態。 */
    const val PRESENTATION_AUTOMATIC_CONTROL_STATUS_DESCRIPTION: String = "mahjongcraft.client_config.presentation.automatic_control_status.description"

    /** 顯示開關：和牌結算。 */
    const val PRESENTATION_WIN_SETTLEMENT: String = "mahjongcraft.client_config.presentation.win_settlement"

    /** 顯示開關說明：和牌結算。 */
    const val PRESENTATION_WIN_SETTLEMENT_DESCRIPTION: String = "mahjongcraft.client_config.presentation.win_settlement.description"

    /** 顯示開關：流局結算。 */
    const val PRESENTATION_DRAW_SETTLEMENT: String = "mahjongcraft.client_config.presentation.draw_settlement"

    /** 顯示開關說明：流局結算。 */
    const val PRESENTATION_DRAW_SETTLEMENT_DESCRIPTION: String = "mahjongcraft.client_config.presentation.draw_settlement.description"

    /** 顯示開關：整場結算。 */
    const val PRESENTATION_MATCH_SETTLEMENT: String = "mahjongcraft.client_config.presentation.match_settlement"

    /** 顯示開關說明：整場結算。 */
    const val PRESENTATION_MATCH_SETTLEMENT_DESCRIPTION: String = "mahjongcraft.client_config.presentation.match_settlement.description"

    /** 顯示開關：相同牌標示。 */
    const val PRESENTATION_MATCHING_TILE_HIGHLIGHT: String = "mahjongcraft.client_config.presentation.matching_tile_highlight"

    /** 顯示開關說明：相同牌標示。 */
    const val PRESENTATION_MATCHING_TILE_HIGHLIGHT_DESCRIPTION: String = "mahjongcraft.client_config.presentation.matching_tile_highlight.description"

    /** 最新捨牌標示開關名稱。 */
    const val PRESENTATION_LATEST_DISCARD_HIGHLIGHT: String = "mahjongcraft.client_config.presentation.latest_discard_highlight"

    /** 最新捨牌標示開關說明。 */
    const val PRESENTATION_LATEST_DISCARD_HIGHLIGHT_DESCRIPTION: String =
        "mahjongcraft.client_config.presentation.latest_discard_highlight.description"

    /** 捨牌來源開關名稱。 */
    const val PRESENTATION_DISCARD_SOURCE: String = "mahjongcraft.client_config.presentation.discard_source"

    /** 捨牌來源開關說明。 */
    const val PRESENTATION_DISCARD_SOURCE_DESCRIPTION: String = "mahjongcraft.client_config.presentation.discard_source.description"

    /** 顯示開關：打牌提示。 */
    const val PRESENTATION_DISCARD_POPUP: String = "mahjongcraft.client_config.presentation.discard_popup"

    /** 顯示開關說明：打牌提示。 */
    const val PRESENTATION_DISCARD_POPUP_DESCRIPTION: String = "mahjongcraft.client_config.presentation.discard_popup.description"

    /** 顯示開關：鳴牌提示。 */
    const val PRESENTATION_MELD_POPUP: String = "mahjongcraft.client_config.presentation.meld_popup"

    /** 顯示開關說明：鳴牌提示。 */
    const val PRESENTATION_MELD_POPUP_DESCRIPTION: String = "mahjongcraft.client_config.presentation.meld_popup.description"

    /** HUD 位置編輯器入口。 */
    const val EDIT_HUD_LAYOUT: String = "mahjongcraft.client_config.edit_hud_layout"

    /** HUD 位置編輯器入口說明。 */
    const val EDIT_HUD_LAYOUT_DESCRIPTION: String = "mahjongcraft.client_config.edit_hud_layout.description"

    /** 自動操作狀態 HUD 顯示的溢出摘要。 */
    const val HUD_AUTOMATIC_CONTROL_MORE: String = "mahjongcraft.hud.automatic_control_more"

    /** HUD 位置編輯器標題。 */
    const val HUD_LAYOUT_TITLE: String = "mahjongcraft.hud_layout.title"

    /** 操作面板預覽名稱。 */
    const val HUD_LAYOUT_DECISION_PANEL: String = "mahjongcraft.hud_layout.decision_panel"

    /** 一般倒數與等待提醒預覽名稱。 */
    const val HUD_LAYOUT_COMPACT_PROMPT: String = "mahjongcraft.hud_layout.compact_prompt"

    /** 手牌分析預覽名稱。 */
    const val HUD_LAYOUT_DISCARD_ANALYSIS: String = "mahjongcraft.hud_layout.discard_analysis"

    /** HUD 位置編輯器中的自動操作狀態元素名稱。 */
    const val HUD_LAYOUT_AUTOMATIC_CONTROL_STATUS: String = "mahjongcraft.hud_layout.automatic_control_status"

    /** 重設 HUD 配置按鈕。 */
    const val HUD_LAYOUT_RESET: String = "mahjongcraft.hud_layout.reset"

    /** 返回按鈕。 */
    const val BACK: String = "mahjongcraft.client_config.back"

    /** Client Config、HUD 版面編輯器與 Room 設定頁共用的未套用變更確認標題。 */
    const val UNSAVED_CHANGES_TITLE: String = "mahjongcraft.settings.unsaved_changes.title"

    /** 未套用變更確認說明。 */
    const val UNSAVED_CHANGES_MESSAGE: String = "mahjongcraft.settings.unsaved_changes.message"

    /** 套用並返回按鈕。 */
    const val APPLY_AND_BACK: String = "mahjongcraft.settings.unsaved_changes.apply_and_back"

    /** 放棄變更按鈕。 */
    const val DISCARD_CHANGES: String = "mahjongcraft.settings.unsaved_changes.discard_changes"

    /** 繼續編輯按鈕。 */
    const val CONTINUE_EDITING: String = "mahjongcraft.settings.unsaved_changes.continue_editing"

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

    /** 循環切換按鈕 tooltip 的目前值列。 */
    const val CURRENT_VALUE: String = "mahjongcraft.settings.current_value"

    /** 循環切換按鈕 tooltip 的可用選項標題。 */
    const val AVAILABLE_OPTIONS: String = "mahjongcraft.settings.available_options"

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
    const val OPEN_KEY: String = OPEN_CLIENT_CONFIG_KEY

    /** MahjongCraft 按鍵分類。 */
    const val KEY_CATEGORY: String = "key.category.mahjongcraft"
}
