package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlin.time.Duration
import kotlin.uuid.Uuid

/** 接收每次有結果的 AI 決策，供量測使用。 */
fun interface AiDecisionObserver {
    /**
     * 一次決策有了結果。在呼叫端的執行緒上呼叫；這一局已有策略呼叫正在計算而沒有決策時不呼叫。
     *
     * @param gameId 對局。
     * @param outcome 結果是怎麼得到的。
     * @param latency 從開始決策到得到結果的時間，含等待名額、排隊與回到呼叫端。
     */
    fun onDecision(gameId: Uuid, outcome: AiDecisionOutcome, latency: Duration)
}
