package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 知道牌面的讀取者保存格式。 */
@Serializable
sealed interface HistoryTileAudiencePersistenceDto {
    /** 所有合法觀察者都知道牌面。 */
    @Serializable
    @SerialName("public")
    data object Public : HistoryTileAudiencePersistenceDto

    /**
     * 只有指定參與者知道牌面。
     *
     * @property playerIds 知道牌面的參與者識別碼。
     */
    @Serializable
    @SerialName("players")
    data class Players(val playerIds: List<String>) : HistoryTileAudiencePersistenceDto
}
