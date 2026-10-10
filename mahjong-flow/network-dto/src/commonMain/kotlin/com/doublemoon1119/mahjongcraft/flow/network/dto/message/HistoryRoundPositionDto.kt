package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 網路傳輸的局內位置。 */
@Serializable
sealed interface HistoryRoundPositionDto {
    /** 開局位置。 */
    @SerialName("initial")
    @Serializable
    data object Initial : HistoryRoundPositionDto

    /**
     * 指定交易後的位置。
     *
     * @property index 交易索引。
     */
    @SerialName("after_transaction")
    @Serializable
    data class AfterTransaction(val index: Int) : HistoryRoundPositionDto
}
