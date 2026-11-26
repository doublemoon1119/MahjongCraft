package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [DrawSource] 的持久化資料。 */
@Serializable
sealed interface DrawSourcePersistenceDto {
    /** 從活牌依正常順序摸牌。 */
    @Serializable
    @SerialName("live_wall")
    data object LiveWall : DrawSourcePersistenceDto

    /**
     * 動作完成後的補牌。
     *
     * @property action 觸發這次補牌的動作。
     */
    @Serializable
    @SerialName("supplemental")
    data class Supplemental(val action: GameActionPersistenceDto) : DrawSourcePersistenceDto
}
