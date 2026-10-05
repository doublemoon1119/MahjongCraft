package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 批次胡牌展示中的單一贏家。
 *
 * @property seatIndex 贏家座位。
 * @property cueIds 這次和牌值得額外展示的理由 ID，依規則決定的順序排列（例如日麻的自然役滿，倍數高者在前）；
 * 沒有時為空清單。要展示哪一個、怎麼展示由平台決定。
 */
data class WinCelebrationWinner(val seatIndex: Int, val cueIds: List<String> = emptyList())

/**
 * 一次胡牌共用的批次展示請求；多家榮和共享同一張 [winningTileId]。
 */
data class WinCelebrationRequest(
    val winningTileId: Uuid,
    val isTsumo: Boolean,
    val winners: List<WinCelebrationWinner>,
)
