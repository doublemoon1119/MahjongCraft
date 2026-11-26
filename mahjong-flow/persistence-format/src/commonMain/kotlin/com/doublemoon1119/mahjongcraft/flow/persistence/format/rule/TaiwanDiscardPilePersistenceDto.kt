package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import kotlinx.serialization.Serializable

/**
 * [TaiwanDiscardPile] 的完整 persistence DTO。
 *
 * @property entries 依捨牌順序排列的牌河項目。
 */
@Serializable
data class TaiwanDiscardPilePersistenceDto(val entries: List<DiscardEntryPersistenceDto>)
