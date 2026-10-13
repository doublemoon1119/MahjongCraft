package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 宣告式胡牌結算模板、欄位 provider、規則模板綁定與詳情格式化器的凍結式註冊中心。 */
interface WinSettlementPresentationTemplateRegistry {
    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 目前已登記的模板 key。 */
    val templateKeys: Set<String>

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
