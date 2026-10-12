package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

/** 可重複建立完整權威桌況的 development-only 情境。 */
interface DebugGameScenario {
    /** 穩定且完整的 namespaced scenario ID。 */
    val id: String

    /**
     * 依目前同桌玩家建立新的權威情境；結果由載入器驗證後寫入。
     *
     * @param context 目前對局與指令發起者。
     * @return 新的對局狀態與呈現資料。
     */
    fun build(context: DebugGameScenarioContext): DebugGameScenarioResult
}
