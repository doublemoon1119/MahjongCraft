package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歷史對局的結束狀態篩選。 */
@Serializable
enum class HistoryOutcomeFilterDto {
    /** 只顯示正常完成的對局。 */
    @SerialName("completed")
    COMPLETED,

    /** 只顯示已確認中止的對局。 */
    @SerialName("interrupted")
    INTERRUPTED,
}
