package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlRuleDisplayDefinition

/** 擴充登記自動操作的顯示時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface AutomaticControlDisplayRegistrar {
    /** 目前已登記的預設 control ID，以及規則專屬顯示的「規則 ID/control ID」穩定快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記完整描述；凍結後或身分重複時拒絕，不提前呼叫來源。
     *
     * @param definition 集中保存登記身分與內容的描述。
     */
    fun register(definition: AutomaticControlDisplayDefinition)

    /**
     * 登記規則專屬的顯示，覆寫該規則對局中的預設顯示；凍結後或同一規則與控制項重複時拒絕。
     *
     * @param definition 規則、控制項與呈現描述。
     */
    fun register(definition: AutomaticControlRuleDisplayDefinition)
}
