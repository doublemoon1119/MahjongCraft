package com.doublemoon1119.mahjongcraft.flow.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * 客戶端最後收到的權威決策計時。
 *
 * @property gameId 計時所屬遊戲。
 * @property phase 目前決策階段。
 * @property baseRemainingAtSync 收到同步時的基本思考時間。
 * @property reserveRemainingAtSync 收到同步時的保留思考時間。
 * @property receivedAt 收到同步時的客戶端單調時間。
 */
data class ClientDecisionTimerState(
    val gameId: Uuid,
    val phase: PlayerDecisionPhase,
    val baseRemainingAtSync: Duration,
    val reserveRemainingAtSync: Duration,
    val receivedAt: Duration,
) {
    /**
     * 從同步時起經過 [elapsed] 後的剩餘時間；先扣基本思考時間，用完後才扣保留思考時間。
     *
     * 只是依同步值推算，權威逾時仍由伺服器判定。
     *
     * @param elapsed 從同步時起經過的時間；負數視為 0。
     * @return 推算的剩餘時間，不小於 0。
     */
    fun remainingAfter(elapsed: Duration): DecisionTimeRemaining {
        val clamped = elapsed.coerceAtLeast(Duration.ZERO)
        val reserveElapsed = (clamped - baseRemainingAtSync).coerceAtLeast(Duration.ZERO)
        return DecisionTimeRemaining(
            base = (baseRemainingAtSync - clamped).coerceAtLeast(Duration.ZERO),
            reserve = (reserveRemainingAtSync - reserveElapsed).coerceAtLeast(Duration.ZERO),
        )
    }
}
