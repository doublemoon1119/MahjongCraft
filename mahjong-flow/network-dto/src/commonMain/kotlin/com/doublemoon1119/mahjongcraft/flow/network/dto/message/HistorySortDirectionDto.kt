package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歷史清單的排序方向。 */
@Serializable
enum class HistorySortDirectionDto {
    /** 由小到大排序。 */
    @SerialName("asc")
    ASC,

    /** 由大到小排序。 */
    @SerialName("desc")
    DESC,
}
