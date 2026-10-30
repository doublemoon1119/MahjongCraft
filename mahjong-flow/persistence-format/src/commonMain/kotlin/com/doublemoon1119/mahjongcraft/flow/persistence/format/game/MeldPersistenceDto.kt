package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import kotlinx.serialization.Serializable

/**
 * [Meld] 的完整 persistence DTO。
 *
 * @property id 副露的穩定識別碼。
 * @property components 副露攜帶的規則組件。
 * @property type 副露種類。
 * @property tiles 副露包含的牌張。
 * @property sourceTile 副露來源牌張；沒有來源牌張時為 null。
 * @property sourceDirection 副露來源相對於目前玩家的方向。
 * @property publiclyRevealedTileIds 可讓一般觀察者知道牌面的牌張 ID。
 * @property faceDownTileIds 桌面上需要朝下呈現的牌張 ID。
 */
@Serializable
data class MeldPersistenceDto(
    val id: String,
    val components: List<TypedPersistenceDto>,
    val type: MeldTypePersistenceDto,
    val tiles: List<IdentifiedTilePersistenceDto>,
    val sourceTile: IdentifiedTilePersistenceDto?,
    val sourceDirection: RelativeDirectionPersistenceDto,
    val publiclyRevealedTileIds: Set<String>,
    val faceDownTileIds: Set<String>,
)
