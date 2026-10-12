package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 開局準備輸入規格的網路資料。 */
@Serializable
sealed interface RoundPreparationInputSpecDto {
    /** 確認輸入。 */
    @Serializable
    @SerialName("confirmation")
    data object Confirmation : RoundPreparationInputSpecDto

    /**
     * 單選輸入規格。
     *
     * @property optionIds 可選項目的識別碼列表。
     */
    @Serializable
    @SerialName("single_choice")
    data class SingleChoice(val optionIds: List<String>) : RoundPreparationInputSpecDto

    /**
     * 牌張選擇輸入。
     *
     * @property eligibleTileIds 可選牌張識別碼。
     * @property minCount 最少選擇數量。
     * @property maxCount 最多選擇數量。
     */
    @Serializable
    @SerialName("tile_selection")
    data class TileSelection(
        val eligibleTileIds: Set<String>,
        val minCount: Int,
        val maxCount: Int,
    ) : RoundPreparationInputSpecDto
}
