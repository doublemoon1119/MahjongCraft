package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歷史查詢可用的資料範圍。 */
@Serializable
enum class HistoryQueryScopeDto {
    /** 只查詢連線玩家參與的對局。 */
    @SerialName("own")
    OWN,

    /** 查詢伺服器允許公開的全部對局。 */
    @SerialName("all")
    ALL,

    /** 查詢開發環境壓力測試資料庫中的全部對局；權限與查詢語意等同 [ALL]，只是讀取的資料來源不同。 */
    @SerialName("stress_test")
    STRESS_TEST,
}
