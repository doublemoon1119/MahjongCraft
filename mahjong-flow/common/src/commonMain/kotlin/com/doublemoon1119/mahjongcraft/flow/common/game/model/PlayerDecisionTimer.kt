package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * 單一玩家一次決策的基本思考時間與保留思考時間計時狀態。
 *
 * [startedAt] 屬於 runtime 的單調時間軸，不得持久化。伺服器停止前只需將
 * [statusAt] 算出的剩餘保留思考時間寫回 [Game.remainingReserveByPlayerId]；下次 session
 * 重新建立計時器，伺服器關閉期間便不會消耗思考時間。
 *
 * @property playerId 目前具有決策權的玩家。
 * @property startedAt 此次決策開始時的單調時間。
 * @property baseDuration 此次決策重新取得的基本思考時間。
 * @property reserveAtStart 此次決策開始時該玩家剩餘的保留思考時間。
 */
data class PlayerDecisionTimer(
    val playerId: Uuid,
    val startedAt: Duration,
    val baseDuration: Duration,
    val reserveAtStart: Duration,
) {
    init {
        require(startedAt.isFinite() && !startedAt.isNegative()) { "Decision start time must be finite and not negative" }
        require(baseDuration.isFinite() && !baseDuration.isNegative()) { "Base duration must be finite and not negative" }
        require(reserveAtStart.isFinite() && !reserveAtStart.isNegative()) { "Reserve time must be finite and not negative" }
        require(baseDuration.isPositive() || reserveAtStart.isPositive()) {
            "Base duration and reserve time must not both be zero"
        }
    }

    /**
     * 計算 [now] 時的權威剩餘時間。
     *
     * 若時間來源短暫回傳早於 [startedAt] 的值，已經過時間會限制為零，避免倒數增加。
     *
     * @param now 同一個單調時間軸上的目前時間。
     * @return 此次決策在指定時間的 [DecisionTimeStatus]。
     */
    fun statusAt(now: Duration): DecisionTimeStatus {
        require(now.isFinite() && !now.isNegative()) { "Current time must be finite and not negative" }
        val elapsed = (now - startedAt).coerceAtLeast(Duration.ZERO)
        val baseRemaining = (baseDuration - elapsed).coerceAtLeast(Duration.ZERO)
        val reserveConsumed = (elapsed - baseDuration).coerceAtLeast(Duration.ZERO)
        val reserveRemaining = (reserveAtStart - reserveConsumed).coerceAtLeast(Duration.ZERO)
        return DecisionTimeStatus(
            baseRemaining = baseRemaining,
            reserveRemaining = reserveRemaining,
            isTimedOut = elapsed >= baseDuration && reserveConsumed >= reserveAtStart,
        )
    }
}

/**
 * 以指定的基本思考秒數及玩家目前剩餘保留思考時間建立一次決策計時器。
 *
 * 呼叫端會依決策階段提供基本時間和保留時間。一般回合與反應階段可使用玩家共用的保留時間池；
 * 規則選擇階段固定以零保留時間計時。
 *
 * @param baseSeconds 此次決策重新取得的基本思考秒數。
 * @param playerId 目前具有決策權的玩家。
 * @param remainingReserve 玩家在決策開始時剩餘的保留思考時間。
 * @param startedAt 此次決策開始時的單調時間。
 * @param resumedBase 接續被中斷的同一次決策時，其尚未使用的基本思考時間；
 *   `null` 代表這是一次全新的決策，重新取得完整基本思考時間。
 * @return 新建立的 [PlayerDecisionTimer]。
 */
fun startDecisionTimer(
    baseSeconds: Int,
    playerId: Uuid,
    remainingReserve: Duration,
    startedAt: Duration,
    resumedBase: Duration? = null,
): PlayerDecisionTimer = PlayerDecisionTimer(
    playerId = playerId,
    startedAt = startedAt,
    baseDuration = resumedBase ?: baseSeconds.seconds,
    reserveAtStart = remainingReserve,
)

/**
 * 以此設定的 [ActionTimeControl.baseSeconds] 及玩家目前剩餘保留思考時間建立一次決策計時器。
 *
 * @param playerId 目前具有決策權的玩家。
 * @param remainingReserve 玩家在決策開始時剩餘的保留思考時間。
 * @param startedAt 此次決策開始時的單調時間。
 * @param resumedBase 接續被中斷的同一次決策時，其尚未使用的基本思考時間；
 *   `null` 代表這是一次全新的決策，重新取得完整基本思考時間。
 * @return 新建立的 [PlayerDecisionTimer]。
 */
fun ActionTimeControl.startDecisionTimer(
    playerId: Uuid,
    remainingReserve: Duration,
    startedAt: Duration,
    resumedBase: Duration? = null,
): PlayerDecisionTimer = startDecisionTimer(
    baseSeconds = baseSeconds,
    playerId = playerId,
    remainingReserve = remainingReserve,
    startedAt = startedAt,
    resumedBase = resumedBase,
)
