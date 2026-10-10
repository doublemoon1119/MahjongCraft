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

    /** 單選結果。 */
    @Serializable
    @SerialName("choice")
    /**
     * Choice 的 persistence 資料型別。
     *
     * @property optionId 選取項目的識別碼。
     */
    data class Choice(val optionId: String) : RoundPreparationSubmissionPersistenceDto

    /** 牌張選擇結果。 */
    @Serializable
    @SerialName("tiles")
    /**
     * Tiles 的 persistence 資料型別。
     *
     * @property tileIds 牌張識別碼列表。
     */
    data class Tiles(val tileIds: Set<String>) : RoundPreparationSubmissionPersistenceDto
}
