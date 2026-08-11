package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 單一玩家一次決策的 A+B 計時狀態。
 *
 * [startedAtMillis] 屬於 runtime 的單調時間軸，不得持久化。伺服器停止前只需將
 * [statusAt] 算出的剩餘 B 寫回 [Game.remainingReserveMillisByPlayerId]；下次 session
 * 重新建立計時器，伺服器關閉期間便不會消耗思考時間。
 *
 * @property playerId 目前具有決策權的玩家。
 * @property startedAtMillis 此次決策開始的單調時間毫秒數。
 * @property actionDurationMillis 此次決策重新取得的 A 時間毫秒數。
 * @property reserveAtStartMillis 此次決策開始時該玩家剩餘的 B 時間毫秒數。
 */
data class PlayerDecisionTimer(
    val playerId: Uuid,
    val startedAtMillis: Long,
    val actionDurationMillis: Long,
    val reserveAtStartMillis: Long,
) {
    init {
        require(startedAtMillis >= 0L) { "Decision start time must not be negative" }
        require(actionDurationMillis >= 0L) { "Action duration must not be negative" }
        require(reserveAtStartMillis >= 0L) { "Reserve time must not be negative" }
        require(actionDurationMillis > 0L || reserveAtStartMillis > 0L) {
            "Action duration and reserve time must not both be zero"
        }
    }

    /**
     * 計算 [nowMillis] 時的權威剩餘時間。
     *
     * 若時間來源短暫回傳早於 [startedAtMillis] 的值，已經過時間會限制為零，避免倒數增加。
     *
     * @param nowMillis 同一個單調時間軸上的目前時間。
     * @return 此次決策在指定時間的 [PlayerDecisionTimeStatus]。
     */
    fun statusAt(nowMillis: Long): PlayerDecisionTimeStatus {
        require(nowMillis >= 0L) { "Current time must not be negative" }
        val elapsedMillis = (nowMillis - startedAtMillis).coerceAtLeast(0L)
        val actionRemainingMillis = (actionDurationMillis - elapsedMillis).coerceAtLeast(0L)
        val reserveConsumedMillis = (elapsedMillis - actionDurationMillis).coerceAtLeast(0L)
        val reserveRemainingMillis = (reserveAtStartMillis - reserveConsumedMillis).coerceAtLeast(0L)
        return PlayerDecisionTimeStatus(
            actionRemainingMillis = actionRemainingMillis,
            reserveRemainingMillis = reserveRemainingMillis,
            isTimedOut = elapsedMillis >= actionDurationMillis && reserveConsumedMillis >= reserveAtStartMillis,
        )
    }
}

/**
 * 玩家一次決策在指定時間點的權威 A+B 計時結果。
 *
 * @property actionRemainingMillis 尚未使用的 A 時間毫秒數。
 * @property reserveRemainingMillis 玩家整場共用的剩餘 B 時間毫秒數。
 * @property isTimedOut A 與 B 是否都已耗盡。
 */
data class PlayerDecisionTimeStatus(
    val actionRemainingMillis: Long,
    val reserveRemainingMillis: Long,
    val isTimedOut: Boolean,
)

/**
 * 以此設定及玩家目前剩餘 B 建立一次決策計時器。
 *
 * @param playerId 目前具有決策權的玩家。
 * @param remainingReserveMillis 玩家在決策開始時剩餘的 B 時間毫秒數。
 * @param startedAtMillis 此次決策開始的單調時間毫秒數。
 * @return 新建立的 [PlayerDecisionTimer]。
 */
fun ActionTimeControl.startDecisionTimer(
    playerId: Uuid,
    remainingReserveMillis: Long,
    startedAtMillis: Long,
): PlayerDecisionTimer = PlayerDecisionTimer(
    playerId = playerId,
    startedAtMillis = startedAtMillis,
    actionDurationMillis = actionSeconds * 1_000L,
    reserveAtStartMillis = remainingReserveMillis,
)
