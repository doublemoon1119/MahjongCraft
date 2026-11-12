package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig

/**
 * 內建規則設定畫面共用的規則／分數分類與欄位建構函式。
 * 流程分類與欄位由 [SharedGameFlowConfigPresentation] 統一附加，不由各規則自行建立。
 *
 * 每個欄位都明確寫出完整 ID 與翻譯鍵；建構函式不從名稱組出 ID 或翻譯鍵。
 */
internal object BuiltInGameConfigFields {
    /** 規則分類。 */
    const val RULE = "mahjongcraft:rule"

    /** 分數分類。 */
    const val SCORE = "mahjongcraft:score"

    /** 規則分類名稱。 */
    const val RULE_NAME_KEY = "mahjongcraft.room.config.category.rule"

    /** 分數分類名稱。 */
    const val SCORE_NAME_KEY = "mahjongcraft.room.config.category.score"

    /**
     * 內建規則提供的規則與分數分類，依畫面順序排列；流程分類由 registry 統一附加。
     *
     * @return 內建規則提供的分類定義。
     */
    fun categories(): List<GameConfigCategoryDefinition> = listOf(
        GameConfigCategoryDefinition(RULE, RULE_NAME_KEY),
        GameConfigCategoryDefinition(SCORE, SCORE_NAME_KEY),
    )

    /**
     * 建立可編輯的開關欄位。
     *
     * @param id 欄位的完整 namespaced ID。
     * @param category 欄位所屬分類 ID。
     * @param nameKey 欄位名稱的翻譯鍵。
     * @param descriptionKey 欄位說明的翻譯鍵。
     * @param read 從遊戲設定讀取開關值的函式。
     * @param update 將新開關值寫回遊戲設定的函式。
     * @return 使用布林切換編輯器的欄位定義。
     */
    fun boolField(
        id: String,
        category: String,
        nameKey: String,
        descriptionKey: String,
        read: (GameConfig) -> Boolean,
        update: (GameConfig, Boolean) -> GameConfig,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id = id,
        categoryId = category,
        nameTranslationKey = nameKey,
        descriptionTranslationKey = descriptionKey,
        editor = GameConfigEditorSpec.BooleanToggle,
        isEditable = true,
        read = { GameConfigPresentationValue.BooleanValue(read(it)) },
        update = { config, value -> update(config, (value as GameConfigPresentationValue.BooleanValue).enabled) },
    )

    /**
     * 建立可編輯的整數欄位。
     *
     * @param id 欄位的完整 namespaced ID。
     * @param category 欄位所屬分類 ID。
     * @param nameKey 欄位名稱的翻譯鍵。
     * @param descriptionKey 欄位說明的翻譯鍵。
     * @param minimum 可輸入的最小值。
     * @param maximum 可輸入的最大值。
     * @param step 一般輸入的步進值。
     * @param read 從遊戲設定讀取整數值的函式。
     * @param update 將新整數值寫回遊戲設定的函式。
     * @return 使用整數輸入編輯器的欄位定義。
     */
    fun intField(
        id: String,
        category: String,
        nameKey: String,
        descriptionKey: String,
        minimum: Int,
        maximum: Int,
        step: Int = 1,
        read: (GameConfig) -> Int,
        update: (GameConfig, Int) -> GameConfig,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id = id,
        categoryId = category,
        nameTranslationKey = nameKey,
        descriptionTranslationKey = descriptionKey,
        editor = GameConfigEditorSpec.IntegerInput(minimum = minimum, maximum = maximum, step = step),
        isEditable = true,
        read = { GameConfigPresentationValue.IntegerValue(read(it)) },
        update = { config, value -> update(config, requireNotNull((value as GameConfigPresentationValue.IntegerValue).number)) },
    )

    /**
     * 建立可編輯的單選欄位；選項 ID、翻譯鍵與設定值都由 [options] 明確對應。
     *
     * @param T 選項對應的設定值型別。
     * @param id 欄位的完整 namespaced ID。
     * @param category 欄位所屬分類 ID。
     * @param nameKey 欄位名稱的翻譯鍵。
     * @param descriptionKey 欄位說明的翻譯鍵。
     * @param options 依畫面順序排列的選項；設定值不可重複。
     * @param read 從遊戲設定讀取目前設定值的函式。
     * @param update 將新設定值寫回遊戲設定的函式。
     * @return 使用單選編輯器的欄位定義。
     */
    fun <T> choiceField(
        id: String,
        category: String,
        nameKey: String,
        descriptionKey: String,
        options: List<BuiltInGameConfigOption<T>>,
        read: (GameConfig) -> T,
        update: (GameConfig, T) -> GameConfig,
    ): GameConfigFieldDefinition {
        require(options.map { it.value }.distinct().size == options.size) { "Config choice values must be unique: $id" }
        return GameConfigFieldDefinition(
            id = id,
            categoryId = category,
            nameTranslationKey = nameKey,
            descriptionTranslationKey = descriptionKey,
            editor = GameConfigEditorSpec.SingleChoice(
                optionIds = options.map { it.id },
                optionTranslationKeys = options.associate { it.id to it.translationKey },
            ),
            isEditable = true,
            read = { config ->
                val value = read(config)
                val option = requireNotNull(options.firstOrNull { it.value == value }) { "Config field $id has no option for value $value" }
                GameConfigPresentationValue.ChoiceValue(option.id)
            },
            update = { config, value ->
                val optionId = (value as GameConfigPresentationValue.ChoiceValue).optionId
                update(config, options.single { it.id == optionId }.value)
            },
        )
    }
}
