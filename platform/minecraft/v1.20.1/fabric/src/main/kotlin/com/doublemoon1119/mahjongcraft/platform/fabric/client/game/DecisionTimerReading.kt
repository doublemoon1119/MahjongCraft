package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
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
