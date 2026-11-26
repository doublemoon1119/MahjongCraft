package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlinx.serialization.Serializable

/** [Tile.Honor] 種類的 persistence 值。 */
@Serializable
enum class HonorPersistenceValue {
    /** 東。 */
    EAST,

    /** 南。 */
    SOUTH,

    /** 西。 */
    WEST,

    /** 北。 */
    NORTH,

    /** 中。 */
    RED,

    /** 發。 */
    GREEN,

    /** 白。 */
    WHITE,
}
