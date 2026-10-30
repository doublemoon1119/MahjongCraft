package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.IdentifiedTilePersistenceDto
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import kotlinx.serialization.Serializable

/**
 * [DiscardPile.DiscardEntry] 的共用 persistence DTO。
 *
 * @property tile 被捨出的牌張。
 * @property isTaken 是否已被其他玩家鳴走。
 * @property components 這次捨牌的紀錄組件，與牌張本身的組件分開保存。
 */
@Serializable
data class DiscardEntryPersistenceDto(
    val tile: IdentifiedTilePersistenceDto,
    val isTaken: Boolean,
    val components: List<TypedPersistenceDto>,
)
