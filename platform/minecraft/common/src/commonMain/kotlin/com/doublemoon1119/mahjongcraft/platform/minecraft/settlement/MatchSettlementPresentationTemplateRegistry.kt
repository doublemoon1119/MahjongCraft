package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** 內建通用終局結算模板 key；規則沒有綁定模板時使用。 */
const val BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY: String = "${MinecraftModMetadata.MOD_ID}:generic_match_settlement"

/** 終局面板模板的凍結式 registry。 */
interface MatchSettlementPresentationTemplateRegistry {
    /** 目前已登記模板 key 的快照。 */
    val registrationKeys: Set<String>

    /** 登記一個完整模板；重複 key 視為錯誤。 */
    fun register(template: MatchSettlementPresentationTemplate)

    /**
     * 指定規則模組使用的模板。
     *
     * @param ruleModuleId 規則模組 ID。
     * @param templateKey 已登記或之後會登記的模板 key。
     */
    fun bindRuleTemplate(ruleModuleId: String, templateKey: String)

    /** 查詢指定 key 的模板。 */
    fun find(key: String): MatchSettlementPresentationTemplate?

    /**
     * 取得規則模組使用的模板；沒有綁定或綁定的模板不存在時使用 [BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY]。
     *
     * @param ruleModuleId 規則模組 ID。
     * @return 模板；連內建模板都不存在時為 null。
     */
    fun findForRule(ruleModuleId: String): MatchSettlementPresentationTemplate?

    /** 凍結 registry，之後不可再登記。 */
    fun freeze()
}
