package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 設定欄位可使用的受控編輯器種類。 */
sealed interface GameConfigEditorSpec {
    /**
     * 判斷欄位值是否為此編輯器能產生的值；房間畫面驗證輸入與伺服器驗證送來的設定共用此判斷。
     *
     * 整數只檢查範圍與可否為空，不檢查步長，因為玩家可以直接輸入數字。
     *
     * @param value 要判斷的欄位值。
     * @return 值的種類相符且落在編輯器限制內時為 true。
     */
    fun accepts(value: GameConfigPresentationValue): Boolean = when (this) {
        BooleanToggle -> value is GameConfigPresentationValue.BooleanValue
        is IntegerInput ->
            value is GameConfigPresentationValue.IntegerValue &&
                (value.number?.let { it in minimum..maximum } ?: nullable)
        is SingleChoice -> value is GameConfigPresentationValue.ChoiceValue && value.optionId in optionIds
        is MultiChoice ->
            value is GameConfigPresentationValue.MultiChoiceValue &&
                value.optionIds.all { it in optionIds } &&
                value.optionIds.size in minimumCount..maximumCount
    }

    /** 布林切換編輯器。 */
    data object BooleanToggle : GameConfigEditorSpec

    /** 從固定選項中選取的編輯器；單選與多選共用選項清單與選項文字查詢。 */
    sealed interface ChoiceEditor : GameConfigEditorSpec {
        /** 可選的完整 Namespaced ID，順序也是顯示順序。 */
        val optionIds: List<String>

        /**
         * 取得選項的翻譯鍵。
         *
         * @param optionId 選項 ID。
         * @return 已登記的翻譯鍵；未登記時為 null。
         */
        fun translationKeyFor(optionId: String): String?
    }

    /**
     * 整數輸入編輯器。
     *
     * @property minimum 最小值。
     * @property maximum 最大值。
     * @property step 輸入步進值。
     * @property nullable 是否允許空值。
     * @property unitTranslationKey 單位翻譯鍵。
     */
    data class IntegerInput(
        val minimum: Int,
        val maximum: Int,
        val step: Int = 1,
        val nullable: Boolean = false,
        val unitTranslationKey: String? = null,
    ) : GameConfigEditorSpec {
        init {
            require(minimum <= maximum) { "Config integer minimum must not exceed maximum" }
            require(step > 0) { "Config integer step must be positive" }
        }
    }

    /**
     * 單一選項編輯器。
     *
     * @property optionIds 可選的完整 Namespaced ID。
     * @property optionTranslationKeys 各選項 ID 對應的翻譯鍵；未提供時由呈現層顯示完整 ID。
     */
    data class SingleChoice(
        override val optionIds: List<String>,
        val optionTranslationKeys: Map<String, String> = emptyMap(),
    ) : ChoiceEditor {
        init {
            require(optionIds.isNotEmpty()) { "Config choice must contain at least one option" }
            require(optionIds.distinct().size == optionIds.size) { "Config choice options must be unique" }
            require(optionIds.all(NamespacedId::isValid)) { "Config choice option IDs must be namespaced" }
            require(optionTranslationKeys.keys.all { it in optionIds }) {
                "Config choice translation keys must reference known option IDs"
            }
            require(optionTranslationKeys.values.all(String::isNotBlank)) {
                "Config choice translation keys must not be blank"
            }
        }

        override fun translationKeyFor(optionId: String): String? = optionTranslationKeys[optionId]
    }

    /**
     * 多個選項的受控編輯器。
     *
     * @property optionIds 可選的完整 Namespaced ID，順序也是顯示順序。
     * @property minimumCount 至少要選取的項目數量。
     * @property maximumCount 最多可選取的項目數量。
     * @property optionTranslationKeys 各選項 ID 對應的翻譯鍵；未提供時由呈現層顯示完整 ID。
     * @property optionDescriptionTranslationKeys 各選項 ID 對應的補充說明翻譯鍵；未提供時不顯示說明。
     */
    data class MultiChoice(
        override val optionIds: List<String>,
        val minimumCount: Int = 0,
        val maximumCount: Int = optionIds.size,
        val optionTranslationKeys: Map<String, String> = emptyMap(),
        val optionDescriptionTranslationKeys: Map<String, String> = emptyMap(),
    ) : ChoiceEditor {
        init {
            require(optionIds.isNotEmpty()) { "Config multi-choice must contain at least one option" }
            require(optionIds.distinct().size == optionIds.size) { "Config multi-choice options must be unique" }
            require(optionIds.all(NamespacedId::isValid)) { "Config multi-choice option IDs must be namespaced" }
            require(minimumCount >= 0) { "Config multi-choice minimum count must not be negative" }
            require(minimumCount <= maximumCount) { "Config multi-choice minimum count must not exceed maximum" }
            require(maximumCount <= optionIds.size) { "Config multi-choice maximum count must not exceed options" }
            require(optionTranslationKeys.keys.all { it in optionIds }) {
                "Config multi-choice translation keys must reference known option IDs"
            }
            require(optionTranslationKeys.values.all(String::isNotBlank)) {
                "Config multi-choice translation keys must not be blank"
            }
            require(optionDescriptionTranslationKeys.keys.all { it in optionIds }) {
                "Config multi-choice description keys must reference known option IDs"
            }
            require(optionDescriptionTranslationKeys.values.all(String::isNotBlank)) {
                "Config multi-choice description keys must not be blank"
            }
        }

        override fun translationKeyFor(optionId: String): String? = optionTranslationKeys[optionId]

        /** 取得選項的補充說明翻譯鍵；未登記時回傳 null。 */
        fun descriptionTranslationKeyFor(optionId: String): String? = optionDescriptionTranslationKeys[optionId]
    }
}
