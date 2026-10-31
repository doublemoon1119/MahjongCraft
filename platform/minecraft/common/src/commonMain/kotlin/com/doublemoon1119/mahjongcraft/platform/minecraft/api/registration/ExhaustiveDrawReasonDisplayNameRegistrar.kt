package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawSettlementStatusLabelsDefinition

/** 擴充登記流局原因名稱時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface ExhaustiveDrawReasonDisplayNameRegistrar {
    /** 目前已登記名稱或結算身分用語的流局原因 ID 快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: ExhaustiveDrawReasonDisplayNameDefinition)

    /**
     * 登記這種流局的玩家結算身分用語；重複 ID 會失敗。
     *
     * @param definition 流局身分用語的完整登記描述。
     */
    fun register(definition: ExhaustiveDrawSettlementStatusLabelsDefinition)
}
