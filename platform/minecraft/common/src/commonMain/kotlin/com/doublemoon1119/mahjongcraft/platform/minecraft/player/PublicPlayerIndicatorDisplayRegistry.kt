package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/** 玩家相關公開標記（indicator、option、付款原因）完整 namespaced ID 的凍結式 Minecraft 顯示 registry。 */
interface PublicPlayerIndicatorDisplayRegistry {
    /** 目前已登記 indicator ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一項公開玩家標記的顯示資料。
     *
     * @param id 公開 indicator、option 或付款原因的完整 ID。
     * @param display 本地化名稱與 RGB 顏色設定。
     */
    fun register(id: String, display: PublicPlayerIndicatorDisplay)

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
