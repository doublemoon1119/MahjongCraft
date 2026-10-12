package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 開局準備提交的網路資料。 */
@Serializable
sealed interface RoundPreparationSubmissionDto {
    /** 已確認。 */
    @Serializable
    @SerialName("confirmed")
    data object Confirmed : RoundPreparationSubmissionDto

    /**
     * 單選提交結果。
     *
     * @property optionId 選定的選項識別碼。
     */
    @Serializable
    @SerialName("choice")
    data class Choice(val optionId: String) : RoundPreparationSubmissionDto

    /**
     * 牌張選擇提交結果。
     *
     * @property tileIds 牌的識別碼集合。
     */
    @Serializable
    @SerialName("tiles")
    data class Tiles(val tileIds: Set<String>) : RoundPreparationSubmissionDto
}
