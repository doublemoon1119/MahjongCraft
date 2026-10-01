package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

/** 保存最新查詢的配對識別碼；世界切換或新條件使舊回應失效。 */
internal class HistoryQueryCorrelation {
    /** 目前仍接受回應的要求識別碼。 */
    private var pendingId: String? = null

    /**
     * 以新要求取代上一個要求。
     *
     * @param requestId 新要求的唯一識別碼，不能沿用舊要求。
     */
    fun begin(requestId: String) {
        pendingId = requestId
    }

    /**
     * 配對目前要求，成功後只接受一次。
     *
     * @param requestId 回應攜帶的要求識別碼。
     * @return 是否為尚未完成的最新要求。
     */
    fun complete(requestId: String): Boolean {
        if (pendingId != requestId) return false
        pendingId = null
        return true
    }

    /** 清除目前連線的配對狀態，使所有遲到回應失效。 */
    fun clear() {
        pendingId = null
    }
}
