package com.doublemoon1119.mahjongcraft.flow.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import kotlin.uuid.Uuid

/**
 * 客戶端最後收到的權威決策計時。
 *
 * @property gameId 計時所屬遊戲。
 * @property phase 目前決策階段。
 * @property baseRemainingAtSyncMillis 收到同步時的基本思考時間。
 * @property reserveRemainingAtSyncMillis 收到同步時的保留思考時間。
 * @property receivedAtMillis 收到同步時的客戶端單調時間。
 */
data class ClientDecisionTimerState(
    val gameId: Uuid,
    val phase: PlayerDecisionPhase,
    val baseRemainingAtSyncMillis: Long,
    val reserveRemainingAtSyncMillis: Long,
    val receivedAtMillis: Long,
) {
    /**
     * 從同步時起經過 [elapsedMillis] 後的剩餘時間；先扣基本思考時間，用完後才扣保留思考時間。
     *
     * 只是依同步值推算，權威逾時仍由伺服器判定。
     *
     * @param elapsedMillis 從同步時起經過的毫秒數；負數視為 0。
     * @return 推算的剩餘時間，不小於 0。
     */
    fun remainingAfter(elapsedMillis: Long): DecisionTimeRemaining {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        val reserveElapsed = (elapsed - baseRemainingAtSyncMillis).coerceAtLeast(0L)
        return DecisionTimeRemaining(
            baseMillis = (baseRemainingAtSyncMillis - elapsed).coerceAtLeast(0L),
            reserveMillis = (reserveRemainingAtSyncMillis - reserveElapsed).coerceAtLeast(0L),
        )
    }
}
