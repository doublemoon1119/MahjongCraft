package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 規則選擇提交的持久化資料。 */
@Serializable
sealed interface RuleDecisionSubmissionPersistenceDto {
    /** 已確認。 */
    @Serializable
    @SerialName("confirmed")
    data object Confirmed : RuleDecisionSubmissionPersistenceDto

    /**
     * 單選提交結果。
     *
     * @property optionId 選取項目的識別碼。
     */
    @Serializable
    @SerialName("choice")
    data class Choice(val optionId: String) : RuleDecisionSubmissionPersistenceDto

    /**
     * 牌張選擇提交結果。
     *
     * @property tileIds 已選取的牌張識別碼集合。
     */
    @Serializable
    @SerialName("tiles")
    data class Tiles(val tileIds: Set<String>) : RuleDecisionSubmissionPersistenceDto
}
