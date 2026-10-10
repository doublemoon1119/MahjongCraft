package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 將完整流局原因 ID 映射至 Minecraft translation key，以及這種流局的玩家結算身分用語。 */
interface ExhaustiveDrawReasonDisplayNameRegistry {
    /** 目前已登記名稱或結算身分用語的流局原因 ID 快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 全部已註冊的完整 reason ID。 */
    val reasonIds: Set<String>

    /** 登記 reason ID 的本地化名稱。 */
    fun register(reasonId: String, translationKey: String)

    /** 查詢本地化名稱；未知 ID 回傳 null。 */
    fun find(reasonId: String): String?

    /** 登記這種流局的玩家結算身分用語；重複 ID 會失敗。 */
    fun registerSettlementStatusLabels(
        reasonId: String,
        labels: ExhaustiveDrawSettlementStatusLabels,
    )

    /** 查詢這種流局的玩家結算身分用語；未登記時回傳 null，呈現端改用通用的「受益」用語。 */
    fun findSettlementStatusLabels(reasonId: String): ExhaustiveDrawSettlementStatusLabels?

    /** 凍結 registry。 */
    fun freeze()
}
