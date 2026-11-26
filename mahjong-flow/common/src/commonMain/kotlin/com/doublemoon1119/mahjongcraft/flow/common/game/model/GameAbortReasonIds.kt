package com.doublemoon1119.mahjongcraft.flow.common.game.model

/** 對局無法安全繼續時使用的規則中立終止原因。 */
object GameAbortReasonIds {
    /** 規則提供和牌動作，卻無法產生對應結算。 */
    const val MISSING_WIN_SETTLEMENT = "mahjongcraft:missing_win_settlement"

    /** 牌山耗盡且沒有可接管的回合結果。 */
    const val MISSING_DRAW_SETTLEMENT = "mahjongcraft:missing_draw_settlement"

    /** 規則回傳互相矛盾或無法執行的回合推進結果。 */
    const val INVALID_PROGRESSION = "mahjongcraft:invalid_match_progression"

    /** 目前必須自動行動，但無法產生或執行安全的後備命令。 */
    const val AUTOMATIC_DECISION_FAILED = "mahjongcraft:automatic_decision_failed"

    /** 規則接點執行失敗，無法安全繼續本場。 */
    const val RULE_EXECUTION_FAILED = "mahjongcraft:rule_execution_failed"
}
