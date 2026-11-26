package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdate
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdatePublisher
import kotlin.uuid.Uuid

/** 紀錄權威決策計時更新的測試 publisher。 */
class FakeDecisionTimerUpdatePublisher : DecisionTimerUpdatePublisher {
    /** 依發送順序保存接收玩家與更新內容。 */
    val updates = mutableListOf<Pair<Uuid, DecisionTimerUpdate>>()

    /** 保存一次計時更新。 */
    override suspend fun publish(targetPlayerId: Uuid, update: DecisionTimerUpdate) {
        updates += targetPlayerId to update
    }
}
