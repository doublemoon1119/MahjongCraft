package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementDetailTextFormatterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementRuleTemplateBindingDefinition

/** 擴充登記和牌結算版型時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface WinSettlementPresentationTemplateRegistrar {
    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 目前已登記模板、欄位 provider、規則綁定與詳情格式化器的穩定 key 快照。 */
    val registrationKeys: Set<String>

    /**
     * 登記胡牌結算模板；重複 key 會失敗。
     *
     * @param template 要登記的模板；registry 會保存其獨立快照。
     */
    fun register(template: WinSettlementPresentationTemplate)

    /**
     * 登記結算欄位 provider；重複欄位 ID 會失敗。
     *
     * @param definition 欄位 provider 的完整登記描述。
     */
    fun register(definition: WinSettlementPresentationFieldProviderDefinition)

    /**
     * 指定規則模組使用的模板。
     *
     * @param definition 規則模板綁定的完整登記描述。
     */
    fun register(definition: WinSettlementRuleTemplateBindingDefinition)

    /**
     * 登記胡牌詳情欄位的格式化器。
     *
     * @param definition 詳情格式化器的完整登記描述。
     */
    fun register(definition: WinSettlementDetailTextFormatterDefinition)
}
