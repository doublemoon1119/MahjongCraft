package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.ExhaustiveDrawReasonDisplayNameRegistrar

/** 將完整流局原因 ID 映射至 Minecraft translation key，以及這種流局的玩家結算身分用語；擴充只透過 [ExhaustiveDrawReasonDisplayNameRegistrar] 登記。 */
interface ExhaustiveDrawReasonDisplayNameRegistry : ExhaustiveDrawReasonDisplayNameRegistrar {

    /** 已登記本地化名稱的完整流局原因 ID。 */
    val reasonIds: Set<String>

    /** 已登記玩家結算身分用語的完整流局原因 ID，與名稱登記分開追蹤。 */
    val settlementStatusReasonIds: Set<String>

    /**
     * 查詢流局原因的本地化名稱。
     *
     * @param reasonId 要查詢的完整流局原因 ID。
     * @return 已登記的 translation key；未知 ID 回傳 null。
     */
    fun find(reasonId: String): String?

    /**
     * 查詢這種流局的玩家結算身分用語；未登記時呈現端可省略身分或顯示通用的「受益」，不推測為聽牌或未聽。
     *
     * @param reasonId 要查詢的完整流局原因 ID。
     * @return 已登記的身分用語；未登記時回傳 null。
     */
    fun findSettlementStatusLabels(reasonId: String): ExhaustiveDrawSettlementStatusLabels?

    /** 凍結 registry。 */
    fun freeze()
}
