package com.doublemoon1119.mahjongcraft.platform.minecraft.action

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameActionVocabularyRegistrar

/**
 * 管理各規則動作用語的凍結式 registry；擴充只透過 [GameActionVocabularyRegistrar] 登記。
 *
 * 查詢以「規則模組 ID + 動作 ID」為準，該規則沒有登記時退回中立預設；兩者都沒有時由呼叫端顯示原始 ID。
 */
interface GameActionVocabularyRegistry : GameActionVocabularyRegistrar {

    /**
     * 查詢用語：先查 [ruleModuleId] 的登記，再查中立預設；都沒有時回傳 null。
     *
     * @param ruleModuleId 規則模組 ID；null 時只查中立預設。
     * @param actionId 動作的穩定 ID。
     * @return 找到的規則專屬或中立顯示用語；都未登記時為 null。
     */
    fun find(ruleModuleId: String?, actionId: String): GameActionVocabulary?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()

    companion object {
        /** [registrationKeys] 中代表中立預設的規則位置。 */
        const val DEFAULT_KEY: String = "default"
    }
}
