package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlinx.serialization.Serializable

/** [RelativeDirection] 的 persistence DTO。 */
@Serializable
enum class RelativeDirectionPersistenceDto {
    /** 左側玩家。 */
    LEFT,

    /** 對面玩家。 */
    ACROSS,

    /** 右側玩家。 */
    RIGHT,

    /** 自身。 */
    SELF,
}
