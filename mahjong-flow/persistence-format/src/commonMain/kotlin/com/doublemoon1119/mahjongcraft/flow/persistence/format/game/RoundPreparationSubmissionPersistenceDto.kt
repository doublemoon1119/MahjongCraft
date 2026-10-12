package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 開局準備提交的持久化資料。 */
@Serializable
sealed interface RoundPreparationSubmissionPersistenceDto {
    /** 已確認。 */
    @Serializable
    @SerialName("confirmed")
    data object Confirmed : RoundPreparationSubmissionPersistenceDto

    /**
     * 單選提交結果。
     *
     * @property optionId 選取項目的識別碼。
     */
    @Serializable
    @SerialName("choice")
    data class Choice(val optionId: String) : RoundPreparationSubmissionPersistenceDto

    /**
     * 牌張選擇提交結果。
     *
     * @property tileIds 已選取的牌張識別碼集合。
     */
    @Serializable
    @SerialName("tiles")
    data class Tiles(val tileIds: Set<String>) : RoundPreparationSubmissionPersistenceDto
}
