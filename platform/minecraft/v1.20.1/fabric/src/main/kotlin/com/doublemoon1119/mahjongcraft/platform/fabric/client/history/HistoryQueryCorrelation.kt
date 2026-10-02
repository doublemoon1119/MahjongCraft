package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

/** 歷史查詢回應可完成的要求種類。 */
internal enum class HistoryQueryKind {
    /** 歷史清單要求。 */
    LIST,

    /** 單場摘要要求。 */
    SUMMARY,

    /** 單場規則設定要求。 */
    RULE_SETTINGS,
}

/** 保存最新查詢的配對識別碼與種類；世界切換或新條件使舊回應失效。 */
internal class HistoryQueryCorrelation {
    /** 目前仍接受回應的要求識別碼。 */
    private var pendingId: String? = null

    /** 目前要求的回應種類。 */
    private var pendingKind: HistoryQueryKind? = null

    /**
     * 以新要求取代上一個要求。
     *
     * @param requestId 新要求的唯一識別碼，不能沿用舊要求。
     * @param kind 新要求期待的回應種類。
     */
    fun begin(requestId: String, kind: HistoryQueryKind? = null) {
        pendingId = requestId
        pendingKind = kind
    }

    /**
     * 配對目前要求，成功後只接受一次。
     *
     * @param requestId 回應攜帶的要求識別碼。
     * @param kind 回應種類；若省略則只驗證要求識別碼。
     * @return 是否為尚未完成的最新要求。
     */
    fun complete(requestId: String, kind: HistoryQueryKind? = null): Boolean {
        if (pendingId != requestId) return false
        if (kind != null && pendingKind != null && pendingKind != kind) return false
        pendingId = null
        pendingKind = null
        return true
    }

    /**
     * 只取消目前相同識別碼的要求。
     *
     * @param requestId 要取消的要求識別碼。
     * @return 是否取消了目前仍待回應的要求。
     */
    fun cancel(requestId: String): Boolean {
        if (pendingId != requestId) return false
        pendingId = null
        pendingKind = null
        return true
    }

    /** 清除目前連線的配對狀態，使所有遲到回應失效。 */
    fun clear() {
        pendingId = null
        pendingKind = null
    }
}
