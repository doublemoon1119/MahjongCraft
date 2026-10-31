package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.HistoryDiscardMarkerDisplayRegistrar

/** 將歷史牌河公開標記 ID 映射至呈現方式；沒有登記的標記不橫擺，提示直接顯示 ID；擴充只透過 [HistoryDiscardMarkerDisplayRegistrar] 登記。 */
interface HistoryDiscardMarkerDisplayRegistry : HistoryDiscardMarkerDisplayRegistrar {

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
