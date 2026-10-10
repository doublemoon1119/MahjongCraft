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

    /** 登記所有規則共用的中立預設。 */
    fun registerDefault(actionId: String, vocabulary: GameActionVocabulary)

    /** 登記指定規則模組專屬的用語，覆寫同一個動作的中立預設。 */
    fun register(ruleModuleId: String, actionId: String, vocabulary: GameActionVocabulary)

    /** 查詢用語：先查 [ruleModuleId] 的登記，再查中立預設；都沒有時回傳 null。 */
    fun find(ruleModuleId: String?, actionId: String): GameActionVocabulary?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()

    companion object {
        /** [registrationKeys] 中代表中立預設的規則位置。 */
        const val DEFAULT_KEY: String = "default"
    }
}
