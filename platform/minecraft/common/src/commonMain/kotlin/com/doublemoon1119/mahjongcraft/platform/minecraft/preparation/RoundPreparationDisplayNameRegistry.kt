package com.doublemoon1119.mahjongcraft.platform.minecraft.preparation

/** 將開局準備 step／option 的完整 ID 映射到 Minecraft translation key。 */
interface RoundPreparationDisplayNameRegistry {
    /** 目前已登記準備項目 ID 的快照。 */
    val registrationKeys: Set<String>

    /** Registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一個 step 或 option 的本地化名稱。 */
    fun register(id: String, translationKey: String)

    /** 查詢本地化名稱；未知 ID 回傳 null，呼叫端應顯示完整 ID。 */
    fun find(id: String): String?

    /** 凍結 registry，禁止 runtime 再改變顯示名稱集合。 */
    fun freeze()
}
