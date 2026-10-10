package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 胡牌手牌的持久化表示。
 *
 * @property standingTileIds 不含和牌張的有序立牌 UUID 字串。
 * @property winningTileId 和牌張 UUID 字串；特殊結算時為 null。
 */
@Serializable
data class HistoryWinningHandPersistenceDto(
    val standingTileIds: List<String>,
    val winningTileId: String?,
)
