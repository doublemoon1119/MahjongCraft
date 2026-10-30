package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import kotlinx.serialization.Serializable

/**
 * [IdentifiedTile] 的完整 persistence DTO，保留跨重啟穩定的牌 UUID。
 *
 * @property id 跨重啟穩定的牌 UUID 字串。
 * @property tile 牌張的 persistence DTO。
 * @property components 這張牌本身的完整組件，與所在區域的紀錄組件分開。
 */
@Serializable
data class IdentifiedTilePersistenceDto(
    val id: String,
    val tile: TilePersistenceDto,
    val components: List<TypedPersistenceDto>,
)
