package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 單一玩家一次決策的基本思考時間與保留思考時間計時狀態。
 *
 * [startedAtMillis] 屬於 runtime 的單調時間軸，不得持久化。伺服器停止前只需將
 * [statusAt] 算出的剩餘保留思考時間寫回 [Game.remainingReserveMillisByPlayerId]；下次 session
 * 重新建立計時器，伺服器關閉期間便不會消耗思考時間。
 *
 * @property playerId 目前具有決策權的玩家。
 * @property startedAtMillis 此次決策開始的單調時間毫秒數。
 * @property baseDurationMillis 此次決策重新取得的基本思考時間毫秒數。
 * @property reserveAtStartMillis 此次決策開始時該玩家剩餘的保留思考時間毫秒數。
 */
data class PlayerDecisionTimer(
    val playerId: Uuid,
    val startedAtMillis: Long,
    val baseDurationMillis: Long,
    val reserveAtStartMillis: Long,
) {
    init {
        require(startedAtMillis >= 0L) { "Decision start time must not be negative" }
        require(baseDurationMillis >= 0L) { "Base duration must not be negative" }
        require(reserveAtStartMillis >= 0L) { "Reserve time must not be negative" }
        require(baseDurationMillis > 0L || reserveAtStartMillis > 0L) {
            "Base duration and reserve time must not both be zero"
        }
    }

    /**
     * 計算 [nowMillis] 時的權威剩餘時間。
     *
     * 若時間來源短暫回傳早於 [startedAtMillis] 的值，已經過時間會限制為零，避免倒數增加。
     *
     * @param nowMillis 同一個單調時間軸上的目前時間。
     * @return 此次決策在指定時間的 [DecisionTimeStatus]。
     */
    fun statusAt(nowMillis: Long): DecisionTimeStatus {
        require(nowMillis >= 0L) { "Current time must not be negative" }
        val elapsedMillis = (nowMillis - startedAtMillis).coerceAtLeast(0L)
        val baseRemainingMillis = (baseDurationMillis - elapsedMillis).coerceAtLeast(0L)
        val reserveConsumedMillis = (elapsedMillis - baseDurationMillis).coerceAtLeast(0L)
        val reserveRemainingMillis = (reserveAtStartMillis - reserveConsumedMillis).coerceAtLeast(0L)
        return DecisionTimeStatus(
            baseRemainingMillis = baseRemainingMillis,
            reserveRemainingMillis = reserveRemainingMillis,
            isTimedOut = elapsedMillis >= baseDurationMillis && reserveConsumedMillis >= reserveAtStartMillis,
        )
    }
}

/**
 * 玩家一次決策在指定時間點的權威思考時間計時結果。
 *
 * 此型別只描述 [PlayerDecisionTimer.statusAt] 的純時間計算，不包含玩家身分或決策階段，也不代表
 * manager 中仍然有效的決策。server manager 會另以
 * `com.doublemoon1119.mahjongcraft.flow.server.game.service.ActivePlayerDecisionStatus` 組合玩家、階段與此狀態。
 * 本型別不直接寫入 persistence 或作為網路 DTO。
 *
 * @property baseRemainingMillis 尚未使用的基本思考時間毫秒數。
 * @property reserveRemainingMillis 玩家整場共用的剩餘保留思考時間毫秒數。
 * @property isTimedOut 基本思考時間與保留思考時間是否都已耗盡。
 */
data class DecisionTimeStatus(
    val baseRemainingMillis: Long,
    val reserveRemainingMillis: Long,
    val isTimedOut: Boolean,
)

/**
 * 以此設定及玩家目前剩餘保留思考時間建立一次決策計時器。
 *
 * @param playerId 目前具有決策權的玩家。
 * @param remainingReserveMillis 玩家在決策開始時剩餘的保留思考時間毫秒數。
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
    baseDurationMillis = baseSeconds * 1_000L,
    reserveAtStartMillis = remainingReserveMillis,
)
