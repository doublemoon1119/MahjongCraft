package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryDetailValueTypeKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歷史胡牌詳情值 DTO；只傳輸規則算出的語意資料，顯示文字由接收端依 ID 決定。 */
@Serializable
sealed interface HistoryWinDetailValueDto {
    /**
     * 有單位的數值。
     *
     * @property quantities 依規則順序排列的數值。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.QUANTITIES)
    /**
     * Quantities 網路 DTO。
     *
     * @property quantities 按規則排列的數值清單。
     */
    data class Quantities(val quantities: List<HistoryWinDetailQuantityDto>) : HistoryWinDetailValueDto

    /**
     * 依規則順序排列的條目。
     *
     * @property entries 條目列表。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.ENTRIES)
    data class Entries(val entries: List<EntryDto>) : HistoryWinDetailValueDto {
        /**
         * 單一條目。
         *
         * @property id 規則定義的條目 ID。
         * @property quantity 條目附帶的數值；沒有數值時為 null。
         */
        @Serializable
        data class EntryDto(
            val id: String,
            val quantity: HistoryWinDetailQuantityDto?,
        )
    }

    /**
     * 局內牌參照集合。
     *
     * @property tiles 牌索引列表。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.TILES)
    /**
     * Tiles 網路 DTO。
     *
     * @property tiles 牌索引列表。
     */
    data class Tiles(val tiles: List<Int>) : HistoryWinDetailValueDto
}
