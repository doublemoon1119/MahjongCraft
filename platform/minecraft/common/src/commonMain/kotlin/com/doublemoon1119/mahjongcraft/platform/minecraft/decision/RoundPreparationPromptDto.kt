package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/** 開局準備輸入供呈現層顯示的受控網路表示。 */
@Serializable
sealed interface RoundPreparationPromptDto {
    /** 只需要確認的準備步驟。 */
    @Serializable
    data object Confirmation : RoundPreparationPromptDto

    /**
     * 從穩定 option ID 清單中選擇一項的準備步驟。
     *
     * @property optionIds 可選的準備選項 ID。
     */
    @Serializable
    data class SingleChoice(
        val optionIds: List<String>,
    ) : RoundPreparationPromptDto

    /**
     * 使用實體手牌選擇指定數量牌張的準備步驟。
     *
     * @property eligibleTileIds 可選的實體手牌 ID。
     * @property eligibleTileAssetKeys 與 [eligibleTileIds] 相同順序、只公開給本人的牌面資產。
     * @property minCount 最少選牌數量。
     * @property maxCount 最多選牌數量。
     */
    @Serializable
    data class TileSelection(
        val eligibleTileIds: List<String>,
        val eligibleTileAssetKeys: List<String>,
        val minCount: Int,
        val maxCount: Int,
    ) : RoundPreparationPromptDto
}
