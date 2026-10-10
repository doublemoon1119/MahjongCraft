package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.IdentifiedTileDto
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import kotlinx.serialization.Serializable

/**
 * 台灣麻將沒有立直等額外狀態，直接鏡射 [DiscardPile.DiscardEntry] 基礎欄位即可。
 *
 * @property tile 被打出的牌。
 * @property isTaken 這張牌是否已被其他玩家鳴走。
 */
@Serializable
data class DiscardEntryDto(
    val tile: IdentifiedTileDto,
    val isTaken: Boolean,
)
