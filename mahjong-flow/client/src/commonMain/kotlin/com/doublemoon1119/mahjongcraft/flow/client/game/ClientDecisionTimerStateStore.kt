package com.doublemoon1119.mahjongcraft.flow.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
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
