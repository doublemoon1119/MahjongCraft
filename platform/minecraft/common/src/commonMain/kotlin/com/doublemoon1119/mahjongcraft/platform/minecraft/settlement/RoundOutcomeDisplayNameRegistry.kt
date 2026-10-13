package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 將規則特殊本局結果 ID（例如日麻的流局滿貫）映射至 Minecraft translation key。 */
interface RoundOutcomeDisplayNameRegistry {
    /** 目前已登記結果 ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: RoundOutcomeDisplayNameDefinition)

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
