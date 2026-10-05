package com.doublemoon1119.mahjongcraft.flow.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
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

/**
 * 推算的剩餘思考時間。
 *
 * @property baseMillis 剩餘基本思考時間。
 * @property reserveMillis 剩餘保留思考時間。
 */
data class DecisionTimeRemaining(
    val baseMillis: Long,
    val reserveMillis: Long,
)

/**
 * 保存客戶端最後收到的權威決策計時；如何依本地時間顯示倒數由平台決定。
 *
 * @property clock 客戶端 runtime 的單調時間，用來記錄收到同步的時間。
 */
class ClientDecisionTimerStateStore(
    private val clock: MonotonicClock,
) {
    /** 最後收到且仍有效的權威計時；停止或尚未同步時為 null。 */
    var state: ClientDecisionTimerState? = null
        private set

    /** 套用一次有效的權威計時同步。 */
    fun apply(
        gameId: Uuid,
        phase: PlayerDecisionPhase,
        baseRemainingMillis: Long,
        reserveRemainingMillis: Long,
    ) {
        require(baseRemainingMillis >= 0L) { "Base remaining time must not be negative" }
        require(reserveRemainingMillis >= 0L) { "Reserve remaining time must not be negative" }
        state = ClientDecisionTimerState(
            gameId = gameId,
            phase = phase,
            baseRemainingAtSyncMillis = baseRemainingMillis,
            reserveRemainingAtSyncMillis = reserveRemainingMillis,
            receivedAtMillis = clock.nowMillis(),
        )
    }

    /** 停止指定遊戲的計時；其他遊戲的較新狀態不受影響。 */
    fun stop(gameId: Uuid) {
        if (state?.gameId == gameId) state = null
    }

    /** 清除離開伺服器後不再有效的計時狀態。 */
    fun clear() {
        state = null
    }
}
