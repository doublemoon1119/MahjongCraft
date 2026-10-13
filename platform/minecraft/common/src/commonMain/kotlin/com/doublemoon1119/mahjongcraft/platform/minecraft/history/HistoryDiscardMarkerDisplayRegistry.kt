package com.doublemoon1119.mahjongcraft.platform.minecraft.history

/** 將歷史牌河公開標記 ID 映射至呈現方式；沒有登記的標記不橫擺，提示直接顯示 ID。 */
interface HistoryDiscardMarkerDisplayRegistry {
    /** 目前已登記標記 ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: HistoryDiscardMarkerDisplayDefinition)

    /**
     * 查詢標記的呈現方式；未登記時回傳 null。
     *
     * @param markerId 要查詢的歷史牌河標記 ID。
     * @return 已登記的顯示設定，或未登記時的 null。
     */
    fun find(markerId: String): HistoryDiscardMarkerDisplay?

    /** 凍結 registry。 */
    fun freeze()
}
