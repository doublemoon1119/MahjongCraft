package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/** 歷史查詢的穩定錯誤代碼。 */
enum class HistoryQueryErrorCode {
    /** 伺服器停用查詢。 */
    QUERY_DISABLED,

    /** 發起者沒有該查詢範圍的權限。 */
    ACCESS_DENIED,

    /** 要求參數無效。 */
    INVALID_REQUEST,

    /** 對局不存在、未公開或已清理。 */
    NOT_AVAILABLE,

    /** 已有查詢或資料庫管理工作正在執行。 */
    BUSY,

    /** 查詢頻率超過限制。 */
    RATE_LIMITED,

    /** 查詢超過執行時間上限。 */
    TIMEOUT,

    /** 目前伺服器 session 不可查詢。 */
    DISCONNECTED,

    /** 原始資料、解析工作量或回應超過資源限制。 */
    CONTENT_TOO_LARGE,
}
