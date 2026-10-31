package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementRuleTemplateBindingDefinition

/** 擴充登記整場結算版型時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface MatchSettlementPresentationTemplateRegistrar {
    /** 目前已登記模板 key 的快照。 */
    val registrationKeys: Set<String>

    /**
     * 登記一個完整模板；重複 key 視為錯誤。
     *
     * @param template 要登記的終局模板；registry 會保存其獨立快照。
     */
    fun register(template: MatchSettlementPresentationTemplate)

    /**
     * 指定規則模組使用的模板。
     *
     * @param definition 規則模板綁定的完整登記描述。
     */
    fun register(definition: MatchSettlementRuleTemplateBindingDefinition)
}
