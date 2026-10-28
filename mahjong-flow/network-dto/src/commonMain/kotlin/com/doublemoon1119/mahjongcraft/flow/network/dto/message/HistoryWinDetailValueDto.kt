package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryDetailValueTypeKeys
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歷史胡牌詳情值 DTO；只傳輸規則算出的語意資料，顯示文字由接收端依 ID 決定。 */
@Serializable
sealed interface HistoryWinDetailValueDto {
    /**
     * 直接顯示的公開牌面，不是局內牌索引。
     *
     * @property faces 保留規則順序的牌面。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.TILE_FACES)
    data class TileFaces(val faces: List<TileDto>) : HistoryWinDetailValueDto

    /**
     * 依規則順序排列、各自附有單位的數值清單。
     *
     * @property quantities 依規則順序排列的數值。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.QUANTITIES)
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
}
