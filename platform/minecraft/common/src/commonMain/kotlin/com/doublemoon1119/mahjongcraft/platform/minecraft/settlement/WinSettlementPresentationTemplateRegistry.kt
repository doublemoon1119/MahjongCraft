package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.WinSettlementPresentationTemplateRegistrar

/** 宣告式胡牌結算模板、欄位 provider、規則模板綁定與詳情格式化器的凍結式註冊中心；擴充只透過 [WinSettlementPresentationTemplateRegistrar] 登記。 */
interface WinSettlementPresentationTemplateRegistry : WinSettlementPresentationTemplateRegistrar {

    /** 目前已登記的模板 key。 */
    val templateKeys: Set<String>

    /**
     * 查詢指定 key 的模板。
     *
     * @param key 完整模板 key。
     * @return 已登記的模板；未知 key 回傳 null。
     */
    fun findTemplate(key: String): WinSettlementPresentationTemplate?

    /**
     * 取得規則模組使用的模板；沒有綁定或綁定的模板不存在時使用通用模板。
     *
     * @param ruleModuleId 規則模組 ID。
     * @return 模板；連通用模板都不存在時為 null。
     */
    fun findTemplateForRule(ruleModuleId: String): WinSettlementPresentationTemplate?

    /**
     * 查詢指定欄位的 provider。
     *
     * @param fieldId 要查詢的欄位 ID。
     * @return 已登記的 provider；未登記時回傳 null。
     */
    fun findFieldProvider(fieldId: PresentationFieldId): WinSettlementPresentationFieldProvider?

    /**
     * 取得胡牌詳情欄位的格式化器。
     *
     * @param fieldId 胡牌詳情欄位 ID。
     * @return 已登記的格式化器；沒有登記時為 null。
     */
    fun findDetailTextFormatter(fieldId: String): WinSettlementDetailTextFormatter?

    /** 凍結 registry，禁止後續登記或綁定。 */
    fun freeze()
}
