package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 一次胡牌共用的批次展示請求；多家榮和共享同一張 [winningTileId]。
 *
 * @property winningTileId 這次胡牌共用的牌張識別碼。
 * @property isTsumo 是否為自摸。
 * @property winners 這次一起成立的贏家及其展示理由。
 */
data class WinCelebrationRequest(
    val winningTileId: Uuid,
    val isTsumo: Boolean,
    val winners: List<WinCelebrationWinner>,
)
