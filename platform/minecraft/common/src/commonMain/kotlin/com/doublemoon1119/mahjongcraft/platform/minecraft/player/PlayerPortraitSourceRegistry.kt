package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/** 依 priority 與穩定 provider ID 解析玩家 portrait 的凍結式 registry。 */
interface PlayerPortraitSourceRegistry {
    /** 目前已登記 provider ID 的快照。 */
    val registrationKeys: Set<String> get() = providerIds.toSet()

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 目前已登記 provider ID 的集合。 */
    val providerIds: Set<String>

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: PlayerPortraitSourceDefinition)

    /**
     * 依 provider 優先順序解析玩家 portrait；無 provider 能提供來源時回傳 null。
     *
     * @param context 要解析的玩家身分。
     * @return 第一個提供來源的 provider ID 與來源，或沒有來源時的 null。
     */
    fun resolve(context: PlayerPortraitSourceContext): ResolvedPlayerPortraitSource?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
