package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 單場對局歷史的權威保存狀態。 */
@Serializable
enum class HistoryArchiveStatusDto {
    /** 歷史仍在權威 outbox 或封存流程中處理。 */
    @SerialName("pending")
    PENDING,

    /** 完整歷史已保存且可供查詢。 */
    @SerialName("saved")
    SAVED,

    /** 該場依開局資格政策排除，未建立歷史事件。 */
    @SerialName("excluded")
    EXCLUDED,

    /** 歷史記錄功能目前停用。 */
    @SerialName("disabled")
    DISABLED,

    /** 該場曾記錄但因缺口、容量或轉移中止而無法完整保存。 */
    @SerialName("failed")
    FAILED,

    /** 目前沒有可供查詢的保存證據。 */
    @SerialName("missing")
    MISSING,

    /** 呼叫者無權查詢該場。 */
    @SerialName("denied")
    DENIED,
}
