package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/** 依 priority 與穩定 provider ID 解析玩家 portrait 的凍結式 registry。 */
interface PlayerPortraitSourceRegistry {
    /** 目前已登記 provider ID 的快照。 */
    val registrationKeys: Set<String> get() = providerIds.toSet()

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 目前已登記 provider ID 的集合。 */
    val providerIds: Set<String>

    /** 登記一個 portrait provider。 */
    fun register(providerId: String, priority: Int = 0, provider: PlayerPortraitSourceProvider)

    /** 依 provider 優先順序解析玩家 portrait；無 provider 能提供來源時回傳 null。 */
    fun resolve(context: PlayerPortraitSourceContext): ResolvedPlayerPortraitSource?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
