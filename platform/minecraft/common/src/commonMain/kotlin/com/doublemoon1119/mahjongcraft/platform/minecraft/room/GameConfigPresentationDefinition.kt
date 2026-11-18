package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 一個規則模組提供的設定呈現定義；registry 登記時會自動附加共用流程分類與欄位。
 * 規則只需要提供自己的分類與欄位；共用流程分類與欄位的保留 ID 見 [GameConfigPresentationRegistry]，
 * 不可由規則重複宣告。規則名稱由 RuleModuleDisplayNameRegistry 提供。
 *
 * @property ruleModuleId 規則模組的 Namespaced ID。
 * @property descriptionTranslationKey 設定描述翻譯鍵。
 * @property selectable 是否可在房間畫面選取；可選規則必須能在麻將桌上呈現（[RulePresentationRequirements]），
 *   伺服器只接受可選規則的房間設定。
 * @property unavailableReasonTranslationKey 不可選取時顯示的原因翻譯鍵。
 * @property defaultRuleConfig 建立預設規則設定。
 * @property categories 規則提供的設定分類。
 * @property fields 規則提供的設定欄位。
 * @property variants 規則提供的玩法，依顯示順序排列；標準玩法自動提供，不需要列出。每個玩法都指定同一組識別欄位；
 *   識別欄位與鎖定欄位都只能是本規則宣告的可編輯欄位，值必須是該欄位編輯器能產生的值，且識別欄位不可同時是
 *   鎖定欄位。
 */
data class GameConfigPresentationDefinition(
    val ruleModuleId: String,
    val descriptionTranslationKey: String,
    val selectable: Boolean,
    val unavailableReasonTranslationKey: String? = null,
    val defaultRuleConfig: () -> MahjongRuleConfig,
    val categories: List<GameConfigCategoryDefinition>,
    val fields: List<GameConfigFieldDefinition>,
    val variants: List<GameConfigVariantDefinition> = emptyList(),
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
        val variantIds = variants.map { it.id }
        require(variantIds.distinct().size == variantIds.size) { "Duplicate config variant ID in $ruleModuleId" }
        require(GameConfigVariantDefinition.STANDARD_ID !in variantIds) {
            "Config variant must not use the reserved ID ${GameConfigVariantDefinition.STANDARD_ID} in $ruleModuleId"
        }
        val fieldsById = fields.associateBy { it.id }
        val identifyingFieldIds = variants.firstOrNull()?.identifyingValues?.keys.orEmpty()
        variants.forEach { variant ->
            require(variant.identifyingValues.isNotEmpty()) { "Config variant ${variant.id} must set at least one identifying field" }
            require(variant.identifyingValues.keys == identifyingFieldIds) {
                "Config variant ${variant.id} must set the same identifying fields as the other variants of $ruleModuleId"
            }
            variant.identifyingValues.forEach { (fieldId, value) ->
                requireVariantValue(fieldsById, variant.id, fieldId, value)
            }
            variant.lockedFields.forEach { (fieldId, locked) ->
                require(fieldId !in identifyingFieldIds) { "Config variant ${variant.id} locks an identifying field: $fieldId" }
                requireVariantValue(fieldsById, variant.id, fieldId, locked.value)
            }
        }
    }

    /**
     * 確認玩法指定的欄位存在、可編輯，且值是欄位編輯器能產生的值。
     *
     * @param fieldsById 本規則宣告的欄位。
     * @param variantId 玩法 ID，用於錯誤訊息。
     * @param fieldId 玩法指定的欄位 ID。
     * @param value 玩法指定的值。
     */
    private fun requireVariantValue(
        fieldsById: Map<String, GameConfigFieldDefinition>,
        variantId: String,
        fieldId: String,
        value: GameConfigPresentationValue,
    ) {
        val field = requireNotNull(fieldsById[fieldId]) { "Config variant $variantId references an unknown field: $fieldId" }
        require(field.isEditable) { "Config variant $variantId sets a read-only field: $fieldId" }
        require(field.editor.accepts(value)) { "Config variant $variantId sets an invalid value for field: $fieldId" }
    }
}
