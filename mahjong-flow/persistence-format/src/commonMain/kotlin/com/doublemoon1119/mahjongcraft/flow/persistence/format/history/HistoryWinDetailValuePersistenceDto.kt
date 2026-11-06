package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryDetailValueTypeKeys
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TileFaceViewPersistenceDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 胡牌詳情值的明確持久化種類。 */
@Serializable
sealed interface HistoryWinDetailValuePersistenceDto {
    /**
     * 不含局內牌張身分的公開牌面，保存結算當時裁切的外觀與特徵。
     *
     * @property faces 保留順序與重複值的牌面。
     */
    @Serializable
    @SerialName(HistoryDetailValueTypeKeys.TILE_FACES)
    data class TileFaces(val faces: List<TileFaceViewPersistenceDto>) : HistoryWinDetailValuePersistenceDto

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
         * @property nameQuantity 出現在條目名稱裡的數值；名稱不帶數值時為 null。
         * @property appliesToSomePayersOnly 條目是否只適用於部分付款者。
         */
        @Serializable
        data class Entry(
            val id: String,
            val quantity: HistoryWinDetailQuantityPersistenceDto?,
            val nameQuantity: HistoryWinDetailQuantityPersistenceDto?,
            val appliesToSomePayersOnly: Boolean,
        )
    }
}
