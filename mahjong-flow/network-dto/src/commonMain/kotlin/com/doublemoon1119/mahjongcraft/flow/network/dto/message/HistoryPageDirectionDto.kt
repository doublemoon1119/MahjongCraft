package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歷史清單從游標往哪一邊取一頁。 */
@Serializable
enum class HistoryPageDirectionDto {
    /** 取游標之後的項目。沒有游標時從第一筆開始。 */
    @SerialName("after")
    AFTER,

    /** 取游標之前的項目。沒有游標時取最後一頁。 */
    @SerialName("before")
    BEFORE,
}
