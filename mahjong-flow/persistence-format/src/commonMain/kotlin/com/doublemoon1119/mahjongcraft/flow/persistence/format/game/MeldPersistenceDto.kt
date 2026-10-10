package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import kotlinx.serialization.Serializable

/**
 * [Meld] 的完整 persistence DTO。
 *
 * @property type 副露種類。
 * @property tiles 副露包含的牌張。
 * @property sourceTile 副露來源牌張；沒有來源牌張時為 null。
 * @property sourceDirection 副露來源相對於目前玩家的方向。
 */
@Serializable
data class MeldPersistenceDto(
    val type: MeldTypePersistenceDto,
    val tiles: List<IdentifiedTilePersistenceDto>,
    val sourceTile: IdentifiedTilePersistenceDto?,
    val sourceDirection: RelativeDirectionPersistenceDto,
)
