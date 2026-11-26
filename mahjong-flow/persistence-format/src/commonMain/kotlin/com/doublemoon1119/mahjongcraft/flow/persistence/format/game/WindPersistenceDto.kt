package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlinx.serialization.Serializable

/** [Wind] 的 persistence DTO。 */
@Serializable
enum class WindPersistenceDto {
    /** 東。 */
    EAST,

    /** 南。 */
    SOUTH,

    /** 西。 */
    WEST,

    /** 北。 */
    NORTH,
}
