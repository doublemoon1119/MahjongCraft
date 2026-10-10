package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryDetailValueTypeKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 胡牌詳情值的明確持久化種類。 */
@Serializable
sealed interface HistoryWinDetailValuePersistenceDto {
    /**
     * 有單位的數值。
     *
     * @property quantities 依規則順序排列的數值。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.QUANTITIES)
    data class Quantities(
        val quantities: List<HistoryWinDetailQuantityPersistenceDto>,
    ) : HistoryWinDetailValuePersistenceDto

    /**
     * 局內牌 UUID 清單。
     *
     * @property tileIds 局內牌 UUID 字串。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.TILES)
    data class Tiles(
        val tileIds: List<String>,
    ) : HistoryWinDetailValuePersistenceDto

    /**
     * 可逐項呈現的詳情條目。
     *
     * @property entries 保留原順序的明細條目。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.ENTRIES)
    data class Entries(
        val entries: List<Entry>,
    ) : HistoryWinDetailValuePersistenceDto {
        /**
         * 單一詳情條目。
         *
         * @property id 規則定義的條目 ID。
         * @property quantity 條目附帶的數值；沒有數值時為 null。
         */
        @Serializable
        data class Entry(
            val id: String,
            val quantity: HistoryWinDetailQuantityPersistenceDto?,
        )
    }
}
