package com.doublemoon1119.mahjongcraft.platform.minecraft.event

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.ActionsPerformedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.EventBatchCommittedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MahjongCraftEvent
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MahjongCraftEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchEndedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchStartedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.RoundCompletedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.RoundSettledListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.RuleResultsResolvedListener

/**
 * 平台橋接使用的事件訂閱點集合；預設建立獨立端點，不改變全域註冊狀態。
 *
 * @property matchStarted 對局開始訂閱點。
 * @property roundSettled 一局結算訂閱點。
 * @property matchEnded 對局結束訂閱點。
 * @property actionsPerformed 已成立動作訂閱點。
 * @property roundCompleted 本局完成訂閱點。
 * @property eventBatchCommitted 交易事件批次訂閱點。
 * @property ruleResultsResolved 已成立規則結果訂閱點。
 */
class GameEventSubscriptions(
    val matchStarted: MahjongCraftEvent<MatchStartedListener> = MahjongCraftEvent.create(),
    val roundSettled: MahjongCraftEvent<RoundSettledListener> = MahjongCraftEvent.create(),
    val matchEnded: MahjongCraftEvent<MatchEndedListener> = MahjongCraftEvent.create(),
    val actionsPerformed: MahjongCraftEvent<ActionsPerformedListener> = MahjongCraftEvent.create(),
    val roundCompleted: MahjongCraftEvent<RoundCompletedListener> = MahjongCraftEvent.create(),
    val eventBatchCommitted: MahjongCraftEvent<EventBatchCommittedListener> = MahjongCraftEvent.create(),
    val ruleResultsResolved: MahjongCraftEvent<RuleResultsResolvedListener> = MahjongCraftEvent.create(),
) {
    /** 正式平台橋接取得全域訂閱點的入口。 */
    companion object {
        /** 取得第三方初始化時註冊的全域端點，不另建訂閱點。 */
        fun global(): GameEventSubscriptions = GameEventSubscriptions(
            matchStarted = MahjongCraftEvents.MATCH_STARTED,
            roundSettled = MahjongCraftEvents.ROUND_SETTLED,
            matchEnded = MahjongCraftEvents.MATCH_ENDED,
            actionsPerformed = MahjongCraftEvents.ACTIONS_PERFORMED,
            roundCompleted = MahjongCraftEvents.ROUND_COMPLETED,
            eventBatchCommitted = MahjongCraftEvents.EVENT_BATCH_COMMITTED,
            ruleResultsResolved = MahjongCraftEvents.RULE_RESULTS_RESOLVED,
        )
    }
}
