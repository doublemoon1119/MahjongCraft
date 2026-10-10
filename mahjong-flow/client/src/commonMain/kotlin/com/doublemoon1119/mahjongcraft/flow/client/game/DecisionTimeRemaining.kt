package com.doublemoon1119.mahjongcraft.flow.client.game

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
