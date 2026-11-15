package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig

/**
 * 依規則的設定呈現定義列出玩法、判斷設定目前是哪個玩法、把玩法套用到設定上，並查詢玩法鎖定的欄位。
 *
 * 玩法不另外保存：目前的玩法由識別欄位的值推得（見 [GameConfigVariantDefinition]）。識別欄位只能透過選擇玩法
 * 改變，伺服器也只接受識別欄位符合某個玩法、鎖定欄位符合該玩法的設定（[isConsistent]），所以房間與對局的設定
 * 永遠對得上一個玩法。
 */
object GameConfigVariantResolver {
    /**
     * 列出規則可選的玩法。
     *
     * @param definition 規則的設定呈現定義。
     * @return 標準玩法在前、其後依登記順序排列的玩法。
     */
    fun options(definition: GameConfigPresentationDefinition): List<GameConfigVariantDefinition> = listOf(standard(definition)) + definition.variants

    /**
     * 取得規則的識別欄位；這些欄位不顯示在設定頁。
     *
     * @param definition 規則的設定呈現定義。
     * @return 欄位 ID；規則沒有登記任何玩法時為空。
     */
    fun identifyingFieldIds(definition: GameConfigPresentationDefinition): Set<String> = definition.variants.firstOrNull()?.identifyingValues?.keys.orEmpty()

    /**
     * 取得選擇玩法時可能被改動的欄位：識別欄位與任一玩法的鎖定欄位。
     *
     * @param definition 規則的設定呈現定義。
     * @return 欄位 ID。
     */
    fun variantFieldIds(definition: GameConfigPresentationDefinition): Set<String> = identifyingFieldIds(definition) + definition.variants.flatMap { it.lockedFields.keys }

    /**
     * 判斷設定目前的玩法。
     *
     * @param definition 規則的設定呈現定義。
     * @param config 要判斷的設定；必須通過 [isConsistent]。
     * @return 識別欄位符合的玩法。
     * @throws IllegalStateException 識別欄位不符合任何玩法時拋出。
     */
    fun current(definition: GameConfigPresentationDefinition, config: GameConfig): GameConfigVariantDefinition = checkNotNull(find(definition, config)) { "Config does not match any variant of ${definition.ruleModuleId}" }

    /**
     * 取得設定頁顯示的欄位：識別欄位以外的全部欄位，依原順序排列。
     *
     * @param definition 規則的設定呈現定義。
     * @return 顯示的欄位。
     */
    fun visibleFields(definition: GameConfigPresentationDefinition): List<GameConfigFieldDefinition> {
        val hidden = identifyingFieldIds(definition)
        return definition.fields.filter { it.id !in hidden }
    }

    /**
     * 判斷設定是否對得上一個玩法：識別欄位符合某個玩法，且該玩法的鎖定欄位都是固定的值。
     *
     * @param definition 規則的設定呈現定義。
     * @param config 要檢查的設定。
     * @return 對得上時為 true。
     */
    fun isConsistent(definition: GameConfigPresentationDefinition, config: GameConfig): Boolean {
        val variant = find(definition, config) ?: return false
        val fieldsById = definition.fields.associateBy { it.id }
        return variant.lockedFields.all { (fieldId, locked) -> fieldsById.getValue(fieldId).read(config) == locked.value }
    }

    /**
     * 取得欄位在目前玩法下的鎖定內容。
     *
     * @param definition 規則的設定呈現定義。
     * @param config 目前的設定。
     * @param fieldId 欄位 ID。
     * @return 目前玩法鎖定這個欄位時為鎖定內容，否則為 null。
     */
    fun lockedField(definition: GameConfigPresentationDefinition, config: GameConfig, fieldId: String): GameConfigLockedField? = current(definition, config).lockedFields[fieldId]

    /**
     * 把玩法套用到設定上：識別欄位與這個玩法的鎖定欄位改成玩法指定的值；原本的玩法鎖定、新玩法不鎖定的欄位
     * 還原為規則預設值，因為那個值是原本的玩法強制的，不是玩家選的。其他設定維持原值。
     *
     * @param definition 規則的設定呈現定義。
     * @param config 要套用玩法的設定。
     * @param variantId 要套用的玩法 ID。
     * @return 套用後的設定。
     * @throws IllegalArgumentException 規則沒有這個玩法時拋出。
     */
    fun apply(definition: GameConfigPresentationDefinition, config: GameConfig, variantId: String): GameConfig {
        val variant = requireNotNull(options(definition).firstOrNull { it.id == variantId }) {
            "Unknown config variant for ${definition.ruleModuleId}: $variantId"
        }
        val defaults = GameConfig(ruleConfig = definition.defaultRuleConfig())
        val released = find(definition, config)?.lockedFields?.keys.orEmpty() - variant.lockedFields.keys
        val releasedValues = definition.fields.filter { it.id in released }.associate { it.id to it.read(defaults) }
        val values = releasedValues + variant.identifyingValues + variant.lockedFields.mapValues { (_, locked) -> locked.value }
        return definition.fields.filter { it.id in values }.fold(config) { updated, field ->
            checkNotNull(field.update) { "Config variant field has no updater: ${field.id}" }(updated, values.getValue(field.id))
        }
    }

    /**
     * 確認規則的每個玩法都能彼此區分，也都與標準玩法不同。
     *
     * @param definition 規則的設定呈現定義。
     * @throws IllegalArgumentException 兩個玩法為識別欄位指定了完全相同的值時拋出。
     */
    fun requireDistinctVariants(definition: GameConfigPresentationDefinition) {
        val seen = mutableMapOf<Map<String, GameConfigPresentationValue>, String>()
        options(definition).forEach { variant ->
            val previous = seen.put(variant.identifyingValues, variant.id)
            require(previous == null) {
                "Config variants $previous and ${variant.id} of ${definition.ruleModuleId} set the same identifying values"
            }
        }
    }

    /**
     * 找出識別欄位符合的玩法；供顯示紀錄等不能假設設定已通過審查的場合使用。
     *
     * @param definition 規則的設定呈現定義。
     * @param config 要判斷的設定。
     * @return 符合的玩法；不符合任何玩法時為 null。
     */
    fun find(definition: GameConfigPresentationDefinition, config: GameConfig): GameConfigVariantDefinition? {
        val fieldIds = identifyingFieldIds(definition)
        val values = definition.fields.filter { it.id in fieldIds }.associate { it.id to it.read(config) }
        return options(definition).firstOrNull { it.identifyingValues == values }
    }

    /**
     * 建立規則自動提供的標準玩法：識別欄位全部取規則預設值，沒有鎖定欄位。
     *
     * @param definition 規則的設定呈現定義。
     * @return 標準玩法；規則沒有登記任何玩法時不指定任何欄位。
     */
    private fun standard(definition: GameConfigPresentationDefinition): GameConfigVariantDefinition {
        val fieldIds = identifyingFieldIds(definition)
        val defaults = GameConfig(ruleConfig = definition.defaultRuleConfig())
        return GameConfigVariantDefinition(
            id = GameConfigVariantDefinition.STANDARD_ID,
            nameTranslationKey = MinecraftRoomScreenKeys.VARIANT_STANDARD,
            descriptionTranslationKey = MinecraftRoomScreenKeys.VARIANT_STANDARD_DESCRIPTION,
            identifyingValues = definition.fields.filter { it.id in fieldIds }.associate { it.id to it.read(defaults) },
        )
    }
}
