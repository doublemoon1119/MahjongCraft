package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** 伺服器送給單一真人玩家的權威決策計時更新。 */
sealed interface DecisionTimerUpdate {
    /**
     * 目前有效的決策計時。
     *
     * @property gameId 計時所屬遊戲。
     * @property phase 玩家目前的決策階段。
     * @property baseRemaining 尚未使用的基本思考時間。
     * @property reserveRemaining 尚未使用的保留思考時間。
     */
    data class Active(
        val gameId: Uuid,
        val phase: PlayerDecisionPhase,
        val baseRemaining: Duration,
        val reserveRemaining: Duration,
    ) : DecisionTimerUpdate

    /**
     * 玩家已失去指定遊戲的決策權。
     *
     * @property gameId 應停止顯示計時的遊戲。
     */
    data class Stopped(val gameId: Uuid) : DecisionTimerUpdate
}
