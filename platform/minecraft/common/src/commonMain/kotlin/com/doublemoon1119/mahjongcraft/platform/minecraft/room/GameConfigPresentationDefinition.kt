package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 一個規則模組的完整設定呈現定義；規則名稱仍由 RuleModuleDisplayNameRegistry 提供。
 *
 * @property ruleModuleId 規則模組的 Namespaced ID。
 * @property descriptionTranslationKey 設定描述翻譯鍵。
 * @property selectable 是否可在房間畫面選取；可選規則必須能在麻將桌上呈現（[RulePresentationRequirements]），
 *   伺服器只接受可選規則的房間設定。
 * @property unavailableReasonTranslationKey 不可選取時顯示的原因翻譯鍵。
 * @property defaultRuleConfig 建立預設規則設定。
 * @property categories 規則提供的設定分類。
 * @property fields 規則提供的設定欄位。
 */
data class GameConfigPresentationDefinition(
    val ruleModuleId: String,
    val descriptionTranslationKey: String,
    val selectable: Boolean,
    val unavailableReasonTranslationKey: String? = null,
    val defaultRuleConfig: () -> MahjongRuleConfig,
    val categories: List<GameConfigCategoryDefinition>,
    val fields: List<GameConfigFieldDefinition>,
) {
    init {
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        require(selectable || unavailableReasonTranslationKey != null) {
            "Unselectable rule presentation must provide an unavailable reason: $ruleModuleId"
        }
        val categoryIds = categories.map { it.id }
        require(categoryIds.distinct().size == categoryIds.size) { "Duplicate config category ID in $ruleModuleId" }
        require(fields.map { it.id }.distinct().size == fields.size) { "Duplicate config field ID in $ruleModuleId" }
        require(fields.all { it.categoryId in categoryIds }) { "Config field references an unknown category in $ruleModuleId" }
    }
}
