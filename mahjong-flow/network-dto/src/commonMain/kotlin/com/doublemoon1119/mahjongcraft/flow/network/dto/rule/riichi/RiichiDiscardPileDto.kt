package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DiscardPileDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import kotlinx.serialization.Serializable

/**
 * [RiichiDiscardPile] 的網路 DTO。
 *
 * @property entries 按打牌順序排列的立直牌河項目。
 */
@Serializable
data class RiichiDiscardPileDto(
    val entries: List<RiichiDiscardEntryDto>,
) : DiscardPileDto
