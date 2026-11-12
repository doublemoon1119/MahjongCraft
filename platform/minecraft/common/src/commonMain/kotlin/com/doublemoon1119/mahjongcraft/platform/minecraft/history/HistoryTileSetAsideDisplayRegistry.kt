package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.HistoryTileSetAsideDisplayRegistrar

/** 牌譜上移出牌區的標題與換牌用語；擴充只透過 [HistoryTileSetAsideDisplayRegistrar] 登記。 */
interface HistoryTileSetAsideDisplayRegistry : HistoryTileSetAsideDisplayRegistrar {

    /**
     * 查詢牌區標題。
     *
     * @param areaId 牌區完整識別碼。
     * @return 已登記的翻譯 key；未登記時為 null，呼叫端改用中立的「移出的牌」。
     */
    fun findAreaName(areaId: String): String?

    /**
     * 查詢規則的換牌用語。
     *
     * @param ruleModuleId 對局規則模組識別碼；未知時為 null。
     * @return 已登記的翻譯 key；未登記時為 null，呼叫端改用中立的「換牌」。
     */
    fun findReplacementName(ruleModuleId: String?): String?

    /** 凍結 registry。 */
    fun freeze()
}
