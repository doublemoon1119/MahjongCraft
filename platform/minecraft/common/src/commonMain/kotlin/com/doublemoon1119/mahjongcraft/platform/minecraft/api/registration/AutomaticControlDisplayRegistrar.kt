package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayDefinition

/** 擴充登記自動操作的顯示時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface AutomaticControlDisplayRegistrar {
    /** 目前已登記 control ID 的穩定快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: AutomaticControlDisplayDefinition)
}
