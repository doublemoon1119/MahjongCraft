package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 開局準備輸入規格的持久化資料。 */
@Serializable
sealed interface RoundPreparationInputSpecPersistenceDto {
    /** 確認輸入。 */
    @Serializable
    @SerialName("confirmation")
    data object Confirmation : RoundPreparationInputSpecPersistenceDto

    /** 單選輸入。 */
    @Serializable
    @SerialName("single_choice")
    /**
     * SingleChoice 的 persistence 資料型別。
     *
     * @property optionIds 可選項目的識別碼。
     */
    data class SingleChoice(val optionIds: List<String>) : RoundPreparationInputSpecPersistenceDto

    /** 牌張選擇輸入。 */
    @Serializable
    @SerialName("tile_selection")
    /**
     * TileSelection 的 persistence 資料型別。
     *
     * @property eligibleTileIds 可選取的牌張識別碼。
     * @property minCount 允許選取的最少數量。
     * @property maxCount 允許選取的最多數量。
     */
    data class TileSelection(val eligibleTileIds: Set<String>, val minCount: Int, val maxCount: Int) : RoundPreparationInputSpecPersistenceDto
}
