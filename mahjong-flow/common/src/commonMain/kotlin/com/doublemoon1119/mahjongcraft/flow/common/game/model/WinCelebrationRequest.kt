package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 一次胡牌共用的批次展示請求；多家榮和共享同一張 [winningTileId]。
 */
data class WinCelebrationRequest(
    val winningTileId: Uuid,
    val isTsumo: Boolean,
    val winners: List<WinCelebrationWinner>,
)
