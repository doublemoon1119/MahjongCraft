package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundOutcomeDisplayNameRegistrar

/**
 * 將規則特殊本局結果 ID（例如日麻的流局滿貫）與規則自訂的整場終止原因 ID（例如日麻的最後莊家第一終局）映射至
 * Minecraft translation key；擴充只透過 [RoundOutcomeDisplayNameRegistrar] 登記。
 */
interface RoundOutcomeDisplayNameRegistry : RoundOutcomeDisplayNameRegistrar {

    /**
     * 查詢特殊結果的本地化名稱。
     *
     * @param outcomeId 要查詢的完整特殊結果 ID。
     * @return 已登記的 translation key；未登記時回傳 null。
     */
    fun find(outcomeId: String): String?

    /** 凍結 registry。 */
    fun freeze()
}
