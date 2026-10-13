package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/** 玩家相關公開標記（indicator、option、付款原因）完整 namespaced ID 的凍結式 Minecraft 顯示 registry。 */
interface PublicPlayerIndicatorDisplayRegistry {
    /** 目前已登記 indicator ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: PublicPlayerIndicatorDisplayDefinition)

    /**
     * 查詢指定標記的顯示資料；未登記時回傳 null。
     *
     * @param id 要查詢的公開標記完整 ID。
     * @return 已登記的顯示設定，或未登記時的 null。
     */
    fun find(id: String): PublicPlayerIndicatorDisplay?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
