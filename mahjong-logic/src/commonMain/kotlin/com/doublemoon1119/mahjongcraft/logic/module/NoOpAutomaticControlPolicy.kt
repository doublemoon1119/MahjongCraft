package com.doublemoon1119.mahjongcraft.logic.module

/** 沒有自動操作行為的安全預設 policy。 */
object NoOpAutomaticControlPolicy : AutomaticControlPolicy {
    /** 維持全部合法動作與正常玩家決策。 */
    override fun evaluate(context: AutomaticControlContext): AutomaticControlResult = AutomaticControlResult()
}
