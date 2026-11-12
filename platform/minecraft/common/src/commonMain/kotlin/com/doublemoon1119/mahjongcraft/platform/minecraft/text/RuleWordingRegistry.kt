package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleWordingRegistrar

/**
 * 依規則選擇共用文字說法的凍結式 registry；沒有專屬機制、但說法因規則而異的共用文字在這裡登記。
 * 擴充只透過 [RuleWordingRegistrar] 登記。
 */
interface RuleWordingRegistry : RuleWordingRegistrar {
    /**
     * 查詢一段共用文字的翻譯鍵；規則登記了自己的說法時優先使用，否則使用中立用語。
     *
     * @param ruleModuleId 目前對局的規則模組 ID；不在對局中時為 null，只查中立用語。
     * @param wordingId 共用文字識別碼。
     * @return 翻譯鍵；中立用語也未登記時為 null。
     */
    fun find(ruleModuleId: String?, wordingId: String): String?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
