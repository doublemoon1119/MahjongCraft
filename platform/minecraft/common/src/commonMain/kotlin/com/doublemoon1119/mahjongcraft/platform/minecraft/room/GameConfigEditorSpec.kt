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
    }

    /** 布林切換編輯器。 */
    data object BooleanToggle : GameConfigEditorSpec

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
     */
    data class SingleChoice(
        val optionIds: List<String>,
    ) : GameConfigEditorSpec {
        init {
            require(optionIds.isNotEmpty()) { "Config choice must contain at least one option" }
            require(optionIds.distinct().size == optionIds.size) { "Config choice options must be unique" }
            require(optionIds.all(NamespacedId::isValid)) { "Config choice option IDs must be namespaced" }
        }
    }
}
