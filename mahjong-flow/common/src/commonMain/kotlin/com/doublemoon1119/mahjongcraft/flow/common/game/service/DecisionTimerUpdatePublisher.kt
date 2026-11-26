package com.doublemoon1119.mahjongcraft.flow.common.game.service

import kotlin.uuid.Uuid

/** 將權威決策計時更新傳送給平台上的指定玩家。 */
interface DecisionTimerUpdatePublisher {
    /**
     * 發布一次計時更新。
     *
     * @param targetPlayerId 接收更新的真人玩家。
     * @param update 欲發布的權威計時狀態。
     */
    suspend fun publish(targetPlayerId: Uuid, update: DecisionTimerUpdate)
}
