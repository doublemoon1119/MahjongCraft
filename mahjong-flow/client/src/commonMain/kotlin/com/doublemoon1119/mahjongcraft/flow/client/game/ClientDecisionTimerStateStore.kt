package com.doublemoon1119.mahjongcraft.flow.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import kotlin.uuid.Uuid

/**
 * 客戶端最後收到的權威決策時間與本地內插狀態。
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
)

/**
 * 供 HUD 讀取的客戶端決策時間。
 *
 * @property gameId 計時所屬遊戲。
 * @property phase 目前決策階段。
 * @property baseRemainingMillis 內插後的基本思考時間。
 * @property reserveRemainingMillis 內插後的保留思考時間。
 * @property isSynchronizationStale 是否已超過同步容許間隔並凍結顯示。
 */
data class ClientDecisionTimerReading(
    val gameId: Uuid,
    val phase: PlayerDecisionPhase,
    val baseRemainingMillis: Long,
    val reserveRemainingMillis: Long,
    val isSynchronizationStale: Boolean,
)

/**
 * 保存客戶端最後收到的權威決策計時，並限制本地內插時間。
 *
 * 本地時間只用於畫面平滑顯示；超過 [staleAfterMillis] 後凍結，不自行宣告逾時。
 *
 * @property clock 客戶端 runtime 的單調時間。
 * @property staleAfterMillis 收不到新同步後允許繼續內插的最長時間。
 */
class ClientDecisionTimerStateStore(
    private val clock: MonotonicClock,
    private val staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
) {
    init {
        require(staleAfterMillis > 0L) { "Stale interval must be positive" }
    }

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

    /** 依目前客戶端單調時間產生供 HUD 顯示的內插讀值。 */
    fun reading(): ClientDecisionTimerReading? {
        val current = state ?: return null
        val elapsedMillis = (clock.nowMillis() - current.receivedAtMillis).coerceAtLeast(0L)
        val interpolatedMillis = elapsedMillis.coerceAtMost(staleAfterMillis)
        val baseRemainingMillis = (current.baseRemainingAtSyncMillis - interpolatedMillis).coerceAtLeast(0L)
        val reserveElapsedMillis = (interpolatedMillis - current.baseRemainingAtSyncMillis).coerceAtLeast(0L)
        return ClientDecisionTimerReading(
            gameId = current.gameId,
            phase = current.phase,
            baseRemainingMillis = baseRemainingMillis,
            reserveRemainingMillis = (current.reserveRemainingAtSyncMillis - reserveElapsedMillis).coerceAtLeast(0L),
            isSynchronizationStale = elapsedMillis > staleAfterMillis,
        )
    }

    private companion object {
        /** 每秒同步下允許一次網路或 tick 抖動的預設凍結門檻。 */
        const val DEFAULT_STALE_AFTER_MILLIS = 1_500L
    }
}
