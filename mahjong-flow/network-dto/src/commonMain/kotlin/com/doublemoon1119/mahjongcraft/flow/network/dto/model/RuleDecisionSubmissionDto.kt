package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 規則選擇提交的網路資料。 */
@Serializable
sealed interface RuleDecisionSubmissionDto {
    /** 已確認。 */
    @Serializable
    @SerialName("confirmed")
    data object Confirmed : RuleDecisionSubmissionDto

    /**
     * 單選提交結果。
     *
     * @property optionId 選定的選項識別碼。
     */
    @Serializable
    @SerialName("choice")
    data class Choice(val optionId: String) : RuleDecisionSubmissionDto

    /**
     * 牌張選擇提交結果。
     *
     * @property tileIds 牌的識別碼集合。
     */
    @Serializable
    @SerialName("tiles")
    data class Tiles(val tileIds: Set<String>) : RuleDecisionSubmissionDto
}
