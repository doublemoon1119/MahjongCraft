package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleDecisionDisplayNameRegistrar

/** 將規則選擇選項的完整 ID 映射到 Minecraft translation key；擴充只透過 [RuleDecisionDisplayNameRegistrar] 登記。 */
interface RuleDecisionDisplayNameRegistry : RuleDecisionDisplayNameRegistrar {

    /**
     * 查詢本地化名稱；未知 ID 回傳 null，呼叫端應顯示完整 ID。
     *
     * @param id 要查詢的 step 或 option 完整 ID。
     * @return 登記的翻譯鍵，或未知 ID 時的 null。
     */
    fun find(id: String): String?

    /** 凍結 registry，禁止 runtime 再改變顯示名稱集合。 */
    fun freeze()
}
