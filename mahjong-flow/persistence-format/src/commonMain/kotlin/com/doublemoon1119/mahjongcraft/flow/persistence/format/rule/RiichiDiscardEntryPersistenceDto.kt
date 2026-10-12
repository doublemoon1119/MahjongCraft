package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.IdentifiedTilePersistenceDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import kotlinx.serialization.Serializable

/**
 * [RiichiDiscardEntry] 的完整 persistence DTO。
 *
 * @property tile 牌張資料。
 * @property isRiichi 該牌是否為立直宣告牌。
 * @property isTaken 該牌是否已被其他玩家鳴走。
 */
@Serializable
data class RiichiDiscardEntryPersistenceDto(
    val tile: IdentifiedTilePersistenceDto,
    val isRiichi: Boolean,
    val isTaken: Boolean,
)
