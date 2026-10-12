package com.doublemoon1119.mahjongcraft.logic.module

/** 依規則語意解析玩家啟用的本局自動操作控制。 */
fun interface AutomaticControlPolicy {
    /**
     * 回傳此次決策應立即採用或隱藏的動作。
     *
     * @param context 目前權威桌況、玩家及可用動作。
     * @return 應立即採用或隱藏的動作；不自動處理時由結果表示。
     */
    fun evaluate(context: AutomaticControlContext): AutomaticControlResult
}
