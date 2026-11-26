package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** AI 座位存在與否的篩選。 */
@Serializable
enum class HistoryAiFilterDto {
    /** 只顯示包含 AI 座位的對局。 */
    @SerialName("contains")
    CONTAINS_AI,

    /** 只顯示沒有 AI 座位的對局。 */
    @SerialName("none")
    NO_AI,
}
