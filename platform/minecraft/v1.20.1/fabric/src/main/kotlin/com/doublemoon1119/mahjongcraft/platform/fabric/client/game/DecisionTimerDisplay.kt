package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * HUD 顯示的決策倒數讀值。
 *
 * @property gameId 計時所屬遊戲。
 * @property phase 目前決策階段。
 * @property baseRemainingMillis 顯示的基本思考時間。
 * @property reserveRemainingMillis 顯示的保留思考時間。
 * @property isSynchronizationStale 是否已超過同步容許間隔並凍結顯示。
 */
data class DecisionTimerReading(
    val gameId: Uuid,
    val phase: PlayerDecisionPhase,
    val baseRemainingMillis: Long,
    val reserveRemainingMillis: Long,
    val isSynchronizationStale: Boolean,
)

/**
 * 依最後收到的權威計時與本地時間產生 HUD 倒數；兩次同步之間平滑倒數，太久沒收到同步時凍結，不自行宣告逾時。
 *
 * @property timerStore 最後收到的權威計時。
 * @property clock 客戶端 runtime 的單調時間。
 */
@Single
class DecisionTimerDisplay(
    private val timerStore: ClientDecisionTimerStateStore,
    @Provided private val clock: MonotonicClock,
) {
    /** 目前應顯示的倒數；沒有有效計時時為 null。 */
    fun reading(): DecisionTimerReading? {
        val state = timerStore.state ?: return null
        val elapsedMillis = (clock.nowMillis() - state.receivedAtMillis).coerceAtLeast(0L)
        val remaining = state.remainingAfter(elapsedMillis.coerceAtMost(STALE_AFTER_MILLIS))
        return DecisionTimerReading(
            gameId = state.gameId,
            phase = state.phase,
            baseRemainingMillis = remaining.baseMillis,
            reserveRemainingMillis = remaining.reserveMillis,
            isSynchronizationStale = elapsedMillis > STALE_AFTER_MILLIS,
        )
    }

    internal companion object {
        /** 收不到新同步後允許繼續倒數的最長時間；每秒同步下容許一次網路或 tick 抖動。 */
        const val STALE_AFTER_MILLIS = 1_500L
    }
}
