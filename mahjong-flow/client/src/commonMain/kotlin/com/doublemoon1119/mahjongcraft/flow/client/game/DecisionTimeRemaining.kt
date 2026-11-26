package com.doublemoon1119.mahjongcraft.flow.client.game

import kotlin.time.Duration

/**
 * 推算的剩餘思考時間。
 *
 * @property base 剩餘基本思考時間。
 * @property reserve 剩餘保留思考時間。
 */
data class DecisionTimeRemaining(
    val base: Duration,
    val reserve: Duration,
)
