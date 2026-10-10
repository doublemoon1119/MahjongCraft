package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/** 玩家相關公開標記（indicator、option、付款原因）完整 namespaced ID 的凍結式 Minecraft 顯示 registry。 */
interface PublicPlayerIndicatorDisplayRegistry {
    /** 目前已登記 indicator ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一項公開玩家標記的顯示資料。 */
    fun register(id: String, display: PublicPlayerIndicatorDisplay)

    /** 查詢指定標記的顯示資料；未登記時回傳 null。 */
    fun find(id: String): PublicPlayerIndicatorDisplay?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
