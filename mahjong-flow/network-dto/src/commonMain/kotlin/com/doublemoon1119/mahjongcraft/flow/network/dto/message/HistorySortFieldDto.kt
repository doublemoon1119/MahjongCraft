package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歷史清單的排序欄位。 */
@Serializable
enum class HistorySortFieldDto {
    /** 依對局結束時間排序。 */
    @SerialName("ended_at")
    ENDED_AT,

    /** 依對局持續時間排序。 */
    @SerialName("duration")
    DURATION,

    /** 依查詢玩家的最終名次排序。 */
    @SerialName("own_rank")
    OWN_RANK,

    /** 依查詢玩家的最終分數排序。 */
    @SerialName("own_score")
    OWN_SCORE,
}
