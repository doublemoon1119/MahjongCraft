package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 歷史查詢對外穩定錯誤代碼。 */
@Serializable
enum class HistoryQueryErrorCodeDto {
    /** 伺服器停用查詢。 */
    @SerialName("query_disabled")
    QUERY_DISABLED,

    /** 查詢範圍沒有權限。 */
    @SerialName("access_denied")
    ACCESS_DENIED,

    /** 請求參數無效。 */
    @SerialName("invalid_request")
    INVALID_REQUEST,

    /** 對局不存在、未公開或已清理。 */
    @SerialName("not_available")
    NOT_AVAILABLE,

    /** 查詢資源忙碌。 */
    @SerialName("busy")
    BUSY,

    /** 查詢頻率超過限制。 */
    @SerialName("rate_limited")
    RATE_LIMITED,

    /** 查詢逾時。 */
    @SerialName("timeout")
    TIMEOUT,

    /** 目前 session 不可查詢。 */
    @SerialName("disconnected")
    DISCONNECTED,

    /** 回應超過傳輸大小限制。 */
    @SerialName("content_too_large")
    CONTENT_TOO_LARGE,
}
