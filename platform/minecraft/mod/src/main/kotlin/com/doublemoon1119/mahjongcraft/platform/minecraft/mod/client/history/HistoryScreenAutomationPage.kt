package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

/** 牌譜瀏覽目前所在的頁面，供開發工具操作牌譜畫面時判斷。 */
enum class HistoryScreenAutomationPage {
    /** 對局列表。 */
    LIST,

    /** 列表的篩選條件。 */
    FILTERS,

    /** 一場對局的摘要。 */
    SUMMARY,

    /** 一場對局的規則設定。 */
    RULE_SETTINGS,

    /** 一局的事件。 */
    ROUND_EVENTS,

    /** 一局某一刻的牌面。 */
    ROUND_STATE,

    /** 一局的回放。 */
    ROUND_REPLAY,
}
