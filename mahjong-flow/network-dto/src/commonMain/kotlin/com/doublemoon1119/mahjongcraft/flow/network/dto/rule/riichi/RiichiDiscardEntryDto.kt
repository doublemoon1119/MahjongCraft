package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import kotlinx.serialization.Serializable

/**
 * [RiichiDiscardEntry] 的網路 DTO。
 *
 * @property tile 被打出的牌。
 * @property isRiichi 這張牌是否宣告立直。
 * @property isTaken 這張牌是否已被其他玩家鳴走。
 */
@Serializable
data class RiichiDiscardEntryDto(
    val tile: IdentifiedTileDto,
    val isRiichi: Boolean,
    val isTaken: Boolean,
)
