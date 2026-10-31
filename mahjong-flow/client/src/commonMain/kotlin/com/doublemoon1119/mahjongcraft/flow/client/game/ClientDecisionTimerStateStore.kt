package com.doublemoon1119.mahjongcraft.flow.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import kotlin.time.Duration
import kotlin.uuid.Uuid

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

    /**
     * 套用一次權威計時同步，並記錄接收時的本地單調時間。
     *
     * @param gameId 計時所屬遊戲。
     * @param phase 同步時的決策階段。
     * @param baseRemaining 剩餘基本思考時間。
     * @param reserveRemaining 剩餘保留思考時間。
     * @throws IllegalArgumentException 任一剩餘時間為負數或無限長時拋出。
     */
    fun apply(
        gameId: Uuid,
        phase: PlayerDecisionPhase,
        baseRemaining: Duration,
        reserveRemaining: Duration,
    ) {
        require(baseRemaining.isFinite() && !baseRemaining.isNegative()) { "Base remaining time must be finite and not negative" }
        require(reserveRemaining.isFinite() && !reserveRemaining.isNegative()) { "Reserve remaining time must be finite and not negative" }
        state = ClientDecisionTimerState(
            gameId = gameId,
            phase = phase,
            baseRemainingAtSync = baseRemaining,
            reserveRemainingAtSync = reserveRemaining,
            receivedAt = clock.now(),
        )
    }

    /**
     * 停止指定遊戲的計時；其他遊戲的較新狀態不受影響。
     *
     * @param gameId 要停止計時的遊戲。
     */
    fun stop(gameId: Uuid) {
        if (state?.gameId == gameId) state = null
    }

    /** 清除離開伺服器後不再有效的計時狀態。 */
    fun clear() {
        state = null
    }
}
