package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * [Hand] 的完整 persistence DTO，保留立牌、副露與最後摸牌的區分。
 *
 * @property tiles 手牌中的立牌。
 * @property melds 手牌中的副露。
 * @property lastDrawn 最近摸入且尚未整理的牌張；沒有時為 null。
 */
@Serializable
data class HandPersistenceDto(
    val tiles: List<IdentifiedTilePersistenceDto>,
    val melds: List<MeldPersistenceDto>,
    val lastDrawn: IdentifiedTilePersistenceDto?,
)
