package com.doublemoon1119.mahjongcraft.platform.minecraft.history

/** 將歷史牌河公開標記 ID 映射至呈現方式；沒有登記的標記不橫擺，提示直接顯示 ID。 */
interface HistoryDiscardMarkerDisplayRegistry {
    /** 目前已登記標記 ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一種標記的呈現方式；重複 ID 會失敗。 */
    fun register(
        markerId: String,
        display: HistoryDiscardMarkerDisplay,
    )

    /** 查詢標記的呈現方式；未登記時回傳 null。 */
    fun find(markerId: String): HistoryDiscardMarkerDisplay?

    /** 凍結 registry。 */
    fun freeze()
}
