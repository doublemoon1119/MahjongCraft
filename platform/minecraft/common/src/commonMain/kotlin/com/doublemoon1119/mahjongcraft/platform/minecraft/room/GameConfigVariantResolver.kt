package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig

/**
 * 依規則的設定呈現定義列出玩法、判斷設定目前符合哪個玩法，以及把玩法套用到設定上。
 *
 * 玩法不另外保存：目前的玩法完全由「用來判斷玩法的欄位」的值推得，這些欄位是同一規則各玩法指定過的
 * 欄位的聯集。改動其他欄位不影響判斷結果。
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
     * 取得規則用來判斷玩法的欄位：各玩法指定過的欄位的聯集。
     *
     * @param definition 規則的設定呈現定義。
     * @return 欄位 ID；規則沒有登記任何玩法時為空。
     */
    fun variantFieldIds(definition: GameConfigPresentationDefinition): Set<String> = definition.variants.flatMapTo(mutableSetOf()) { it.fieldValues.keys }

    /**
     * 判斷設定目前符合的玩法。
     *
     * @param definition 規則的設定呈現定義。
     * @param config 要判斷的設定。
     * @return 符合的玩法；用來判斷玩法的欄位不符合任何玩法時為 null。
     */
    fun current(definition: GameConfigPresentationDefinition, config: GameConfig): GameConfigVariantDefinition? {
        val standard = standard(definition)
        val fields = definition.fields.filter { it.id in standard.fieldValues }
        val values = fields.associate { it.id to it.read(config) }
        return options(definition).firstOrNull { variant -> effectiveValues(variant, standard) == values }
    }

    /**
     * 把玩法套用到設定上；只改用來判斷玩法的欄位，其他設定維持原值。
     *
     * @param definition 規則的設定呈現定義。
     * @param config 要套用玩法的設定。
     * @param variantId 要套用的玩法 ID。
     * @return 套用後的設定。
     * @throws IllegalArgumentException 規則沒有這個玩法時拋出。
     */
    fun apply(definition: GameConfigPresentationDefinition, config: GameConfig, variantId: String): GameConfig {
        val standard = standard(definition)
        val variant = requireNotNull(options(definition).firstOrNull { it.id == variantId }) {
            "Unknown config variant for ${definition.ruleModuleId}: $variantId"
        }
        val values = effectiveValues(variant, standard)
        return definition.fields.filter { it.id in values }.fold(config) { updated, field ->
            checkNotNull(field.update) { "Config variant field has no updater: ${field.id}" }(updated, values.getValue(field.id))
        }
    }

    /**
     * 確認規則的每個玩法都能彼此區分，也都與標準玩法不同。
     *
     * @param definition 規則的設定呈現定義。
     * @throws IllegalArgumentException 兩個玩法為用來判斷玩法的欄位指定了完全相同的值時拋出。
     */
    fun requireDistinctVariants(definition: GameConfigPresentationDefinition) {
        val standard = standard(definition)
        val seen = mutableMapOf<Map<String, GameConfigPresentationValue>, String>()
        options(definition).forEach { variant ->
            val previous = seen.put(effectiveValues(variant, standard), variant.id)
            require(previous == null) {
                "Config variants $previous and ${variant.id} of ${definition.ruleModuleId} set the same values"
            }
        }
    }

    /**
     * 建立規則自動提供的標準玩法：用來判斷玩法的欄位全部取規則預設值。
     *
     * @param definition 規則的設定呈現定義。
     * @return 標準玩法；規則沒有登記任何玩法時不指定任何欄位。
     */
    private fun standard(definition: GameConfigPresentationDefinition): GameConfigVariantDefinition {
        val fieldIds = variantFieldIds(definition)
        val defaults = GameConfig(ruleConfig = definition.defaultRuleConfig())
        return GameConfigVariantDefinition(
            id = GameConfigVariantDefinition.STANDARD_ID,
            nameTranslationKey = MinecraftRoomScreenKeys.VARIANT_STANDARD,
            descriptionTranslationKey = MinecraftRoomScreenKeys.VARIANT_STANDARD_DESCRIPTION,
            fieldValues = definition.fields.filter { it.id in fieldIds }.associate { it.id to it.read(defaults) },
        )
    }

    /**
     * 取得玩法為每個用來判斷玩法的欄位指定的值；玩法沒有指定的欄位取標準玩法的值。
     *
     * @param variant 要取值的玩法。
     * @param standard 同一規則的標準玩法。
     * @return 欄位 ID 與其值。
     */
    private fun effectiveValues(
        variant: GameConfigVariantDefinition,
        standard: GameConfigVariantDefinition,
    ): Map<String, GameConfigPresentationValue> = standard.fieldValues + variant.fieldValues
}
