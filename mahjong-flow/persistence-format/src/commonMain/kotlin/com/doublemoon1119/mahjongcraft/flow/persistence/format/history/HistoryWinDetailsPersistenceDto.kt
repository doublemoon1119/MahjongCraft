package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 胡牌詳情的持久化表示。
 *
 * @property playerId 胡牌玩家 UUID 字串。
 * @property detailFields 胡牌詳情欄位。
 * @property hand 結算時的立牌順序與和牌張；未記錄時為 null。
 */
@Serializable
data class HistoryWinDetailsPersistenceDto(
    val playerId: String,
    val detailFields: List<HistoryWinDetailFieldPersistenceDto>,
    val hand: HistoryWinningHandPersistenceDto?,
)
