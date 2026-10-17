package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig

/**
 * 內建規則設定畫面共用的規則／分數分類與欄位建構函式。
 * 流程分類與欄位由 [SharedGameFlowConfigPresentation] 統一附加，不由各規則自行建立。
 *
 * 欄位 ID 為 `mahjongcraft:<name>`；名稱與說明的 translation key 分別為 `mahjongcraft.room.config.field.<name>`
 * 與 `mahjongcraft.room.config.field.<name>.description`。
 */
internal object BuiltInGameConfigFields {
    /** 規則分類。 */
    const val RULE = "mahjongcraft:rule"

    /** 分數分類。 */
    const val SCORE = "mahjongcraft:score"

    /**
     * 內建規則提供的規則與分數分類，依畫面順序排列；流程分類由 registry 統一附加。
     *
     * @return 內建規則提供的分類定義。
     */
    fun categories(): List<GameConfigCategoryDefinition> = listOf(
        GameConfigCategoryDefinition(RULE, translationKey("category.rule")),
        GameConfigCategoryDefinition(SCORE, translationKey("category.score")),
    )

    /**
     * 建立設定畫面使用的 translation key。
     *
     * @param path 設定資源路徑片段。
     * @return 加上設定畫面前綴的翻譯鍵。
     */
    fun translationKey(path: String): String = "mahjongcraft.room.config.$path"

    /**
     * 建立可編輯的開關欄位。
     *
     * @param name 欄位 ID 的路徑片段。
     * @param category 欄位所屬分類 ID。
     * @param read 從遊戲設定讀取開關值的函式。
     * @param update 將新開關值寫回遊戲設定的函式。
     * @return 使用布林切換編輯器的欄位定義。
     */
    fun boolField(
        name: String,
        category: String,
        read: (GameConfig) -> Boolean,
        update: (GameConfig, Boolean) -> GameConfig,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id(name),
        category,
        translationKey("field.$name"),
        translationKey("field.$name.description"),
        GameConfigEditorSpec.BooleanToggle,
        true,
        read = { GameConfigPresentationValue.BooleanValue(read(it)) },
        update = { config, value -> update(config, (value as GameConfigPresentationValue.BooleanValue).enabled) },
    )

    /**
     * 建立可編輯的整數欄位。
     *
     * @param name 欄位 ID 的路徑片段。
     * @param category 欄位所屬分類 ID。
     * @param minimum 可輸入的最小值。
     * @param maximum 可輸入的最大值。
     * @param step 一般輸入的步進值。
     * @param read 從遊戲設定讀取整數值的函式。
     * @param update 將新整數值寫回遊戲設定的函式。
     * @return 使用整數輸入編輯器的欄位定義。
     */
    fun intField(
        name: String,
        category: String,
        minimum: Int,
        maximum: Int,
        step: Int = 1,
        read: (GameConfig) -> Int,
        update: (GameConfig, Int) -> GameConfig,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id(name),
        category,
        translationKey("field.$name"),
        translationKey("field.$name.description"),
        GameConfigEditorSpec.IntegerInput(minimum, maximum, step),
        true,
        read = { GameConfigPresentationValue.IntegerValue(read(it)) },
        update = { config, value -> update(config, requireNotNull((value as GameConfigPresentationValue.IntegerValue).number)) },
    )

    /**
     * 建立可編輯的單選欄位；內建選項 ID 為 `mahjongcraft:<option>`，並明確指定各自的翻譯鍵。
     *
     * @param name 欄位 ID 的路徑片段。
     * @param category 欄位所屬分類 ID。
     * @param options 可選項目的路徑片段。
     * @param read 從遊戲設定讀取選項值的函式。
     * @param update 將新選項值寫回遊戲設定的函式。
     * @return 使用單選編輯器的欄位定義。
     */
    fun choiceField(
        name: String,
        category: String,
        options: List<String>,
        read: (GameConfig) -> String,
        update: (GameConfig, String) -> GameConfig,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id(name),
        category,
        translationKey("field.$name"),
        translationKey("field.$name.description"),
        GameConfigEditorSpec.SingleChoice(
            optionIds = options.map(::option),
            optionTranslationKeys = options.associate { option(it) to translationKey("option.$it") },
        ),
        true,
        read = { GameConfigPresentationValue.ChoiceValue(option(read(it))) },
        update = { config, value -> update(config, (value as GameConfigPresentationValue.ChoiceValue).optionId.substringAfter(':')) },
    )

    /**
     * 建立只顯示、不能編輯的整數欄位。
     *
     * @param name 欄位 ID 的路徑片段。
     * @param category 欄位所屬分類 ID。
     * @param read 從遊戲設定讀取整數值的函式。
     * @return 使用唯讀整數呈現的欄位定義。
     */
    fun readOnlyInt(
        name: String,
        category: String,
        read: (GameConfig) -> Int,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id(name),
        category,
        translationKey("field.$name"),
        translationKey("field.$name.description"),
        GameConfigEditorSpec.IntegerInput(Int.MIN_VALUE, Int.MAX_VALUE),
        false,
        read = { GameConfigPresentationValue.IntegerValue(read(it)) },
    )

    /**
     * 建立只顯示、不能編輯的開關欄位。
     *
     * @param name 欄位 ID 的路徑片段。
     * @param category 欄位所屬分類 ID。
     * @param read 從遊戲設定讀取開關值的函式。
     * @return 使用唯讀布林呈現的欄位定義。
     */
    fun readOnlyBoolean(
        name: String,
        category: String,
        read: (GameConfig) -> Boolean,
    ): GameConfigFieldDefinition = GameConfigFieldDefinition(
        id(name),
        category,
        translationKey("field.$name"),
        translationKey("field.$name.description"),
        GameConfigEditorSpec.BooleanToggle,
        false,
        read = { GameConfigPresentationValue.BooleanValue(read(it)) },
    )

    /**
     * 為內建設定欄位路徑加上固定命名空間。
     *
     * @param path 欄位 ID 的路徑片段。
     * @return 以 `mahjongcraft:` 開頭的完整 ID。
     */
    private fun id(path: String): String = "mahjongcraft:$path"

    /**
     * 為內建單選項目路徑加上固定命名空間。
     *
     * @param path 選項 ID 的路徑片段。
     * @return 以 `mahjongcraft:` 開頭的完整 ID。
     */
    private fun option(path: String): String = "mahjongcraft:$path"
}
