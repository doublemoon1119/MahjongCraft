package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DiscardPileDto
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import kotlinx.serialization.Serializable

/**
 * [TaiwanDiscardPile] 的網路 DTO。
 *
 * @property entries 按打牌順序排列的台灣麻將牌河項目。
 */
@Serializable
data class TaiwanDiscardPileDto(
    val entries: List<DiscardEntryDto>,
) : DiscardPileDto
