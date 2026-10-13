package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 終局結算規則模板綁定的登記描述。
 *
 * @property ruleModuleId 規則模組的穩定識別碼。
 * @property templateKey 要使用的終局模板識別碼。
 */
data class MatchSettlementRuleTemplateBindingDefinition(
    val ruleModuleId: String,
    val templateKey: String,
)
