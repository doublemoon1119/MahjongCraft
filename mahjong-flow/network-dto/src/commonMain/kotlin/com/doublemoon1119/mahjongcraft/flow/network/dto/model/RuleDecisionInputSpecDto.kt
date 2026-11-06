package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 規則選擇輸入規格的網路資料。 */
@Serializable
sealed interface RuleDecisionInputSpecDto {
    /** 確認輸入。 */
    @Serializable
    @SerialName("confirmation")
    data object Confirmation : RuleDecisionInputSpecDto

    /**
     * 單選輸入規格。
     *
     * @property optionIds 可選項目的識別碼列表。
     * @property previewTileIdsByOptionId 各選項要展示的牌張識別碼。
     * @property passOptionId 代表跳過的選項；沒有時為 null。
     */
    @Serializable
    @SerialName("single_choice")
    data class SingleChoice(
        val optionIds: List<String>,
        val previewTileIdsByOptionId: Map<String, List<String>>,
        val passOptionId: String?,
    ) : RuleDecisionInputSpecDto

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
    ) : RuleDecisionInputSpecDto
}
