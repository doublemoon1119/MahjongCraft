package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 將完整流局原因 ID 映射至 Minecraft translation key，以及這種流局的玩家結算身分用語。 */
interface ExhaustiveDrawReasonDisplayNameRegistry {
    /** 目前已登記名稱或結算身分用語的流局原因 ID 快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 已登記本地化名稱的完整流局原因 ID。 */
    val reasonIds: Set<String>

    /**
     * 登記流局原因 ID 的本地化名稱。
     *
     * @param reasonId 完整流局原因 ID。
     * @param translationKey Minecraft translation key。
     */
    fun register(reasonId: String, translationKey: String)

    /**
     * 查詢流局原因的本地化名稱。
     *
     * @param reasonId 要查詢的完整流局原因 ID。
     * @return 已登記的 translation key；未知 ID 回傳 null。
     */
    fun find(reasonId: String): String?

    /**
     * 登記這種流局的玩家結算身分用語；重複 ID 會失敗。
     *
     * @param reasonId 完整流局原因 ID。
     * @param labels 得分玩家與其他玩家的身分用語。
     */
    fun registerSettlementStatusLabels(
        reasonId: String,
        labels: ExhaustiveDrawSettlementStatusLabels,
    )

    /**
     * 查詢這種流局的玩家結算身分用語；未登記時呈現端改用通用的「受益」用語。
     *
     * @param reasonId 要查詢的完整流局原因 ID。
     * @return 已登記的身分用語；未登記時回傳 null。
     */
    fun findSettlementStatusLabels(reasonId: String): ExhaustiveDrawSettlementStatusLabels?

    /** 凍結 registry。 */
    fun freeze()
}
