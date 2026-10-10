package com.doublemoon1119.mahjongcraft.flow.common.game.model

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
