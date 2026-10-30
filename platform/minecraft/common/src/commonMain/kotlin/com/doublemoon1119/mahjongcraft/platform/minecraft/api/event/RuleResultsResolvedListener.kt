package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import com.doublemoon1119.mahjongcraft.flow.api.event.RuleResultsResolvedEvent

/** 已成立規則結果的訂閱者。 */
fun interface RuleResultsResolvedListener {
    /**
     * 處理同筆交易的公開規則結果。
     *
     * @param event 按提交順序排列的規則結果。
     * @param context 事件產生當下的情境。
     */
    fun onRuleResultsResolved(event: RuleResultsResolvedEvent, context: GameEventContext)
}
