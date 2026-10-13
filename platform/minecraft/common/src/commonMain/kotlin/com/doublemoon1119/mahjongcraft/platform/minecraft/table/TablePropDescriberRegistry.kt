package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/** 依規則模組識別碼登記 [TablePropDescriber]；沒有登記的規則，桌上不會擺任何由規則描述的物件。 */
interface TablePropDescriberRegistry {
    /** 目前已登記的規則模組識別碼快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: TablePropDescriberDefinition)

    /**
     * 查詢指定規則模組的描述；未登記時回傳 null。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @return 已登記描述器；未登記時為 `null`。
     */
    fun find(ruleModuleId: String): TablePropDescriber?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
