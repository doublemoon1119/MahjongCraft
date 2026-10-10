package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歷史資料完整性篩選。 */
@Serializable
enum class HistoryIntegrityFilterDto {
    /** 只顯示可完整重播的對局。 */
    @SerialName("complete")
    COMPLETE,

    /** 只顯示只能提供部分結果的對局。 */
    @SerialName("incomplete")
    INCOMPLETE,
}
