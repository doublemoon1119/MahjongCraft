package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 將規則特殊本局結果 ID（例如日麻的流局滿貫）映射至 Minecraft translation key。 */
interface RoundOutcomeDisplayNameRegistry {
    /** 目前已登記結果 ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記結果 ID 的本地化名稱；重複 ID 會失敗。 */
    fun register(
        outcomeId: String,
        translationKey: String,
    )

    /** 查詢本地化名稱；未登記時回傳 null。 */
    fun find(outcomeId: String): String?

    /** 凍結 registry。 */
    fun freeze()
}
