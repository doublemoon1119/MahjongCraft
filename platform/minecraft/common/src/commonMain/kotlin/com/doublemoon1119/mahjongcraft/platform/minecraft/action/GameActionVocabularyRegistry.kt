package com.doublemoon1119.mahjongcraft.platform.minecraft.action

/**
 * 管理各規則動作用語的凍結式 registry。
 *
 * 查詢以「規則模組 ID + 動作 ID」為準，該規則沒有登記時退回中立預設；兩者都沒有時由呼叫端顯示原始 ID。
 */
interface GameActionVocabularyRegistry {
    /** 目前已登記組合的穩定 key 快照，格式為 `規則模組 ID/動作 ID`，中立預設以 [DEFAULT_KEY] 表示。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記所有規則共用的中立預設。
     *
     * @param definition 中立預設的完整登記描述。
     */
    fun register(definition: GameActionVocabularyDefaultDefinition)

    /**
     * 登記指定規則模組專屬的用語，優先於同一動作的中立預設。
     *
     * @param definition 規則專屬用語的完整登記描述。
     */
    fun register(definition: GameActionVocabularyDefinition)

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
