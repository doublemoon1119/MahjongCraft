package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import kotlin.uuid.Uuid

/** 伺服器送給單一真人玩家的權威決策計時更新。 */
sealed interface DecisionTimerUpdate {
    /**
     * 目前有效的決策計時。
     *
     * @property gameId 計時所屬遊戲。
     * @property phase 玩家目前的決策階段。
     * @property baseRemainingMillis 尚未使用的基本思考時間。
     * @property reserveRemainingMillis 尚未使用的保留思考時間。
     */
    data class Active(
        val gameId: Uuid,
        val phase: PlayerDecisionPhase,
        val baseRemainingMillis: Long,
        val reserveRemainingMillis: Long,
    ) : DecisionTimerUpdate

    /**
     * 玩家已失去指定遊戲的決策權。
     *
     * @property gameId 應停止顯示計時的遊戲。
     */
    data class Stopped(val gameId: Uuid) : DecisionTimerUpdate
}

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
