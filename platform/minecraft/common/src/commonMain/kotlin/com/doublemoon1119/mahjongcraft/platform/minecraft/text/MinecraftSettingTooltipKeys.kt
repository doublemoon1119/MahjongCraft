package com.doublemoon1119.mahjongcraft.platform.minecraft.text

/** 設定欄位提示框共用格式的翻譯鍵：狀態區標題與操作提示。 */
object MinecraftSettingTooltipKeys {
    /** 選項清單的標題。 */
    const val OPTIONS: String = "mahjongcraft.settings.tooltip.options"

    /** 多選欄位的選項清單標題，帶已選數量與允許的最少、最多數量。 */
    const val OPTIONS_SELECTED: String = "mahjongcraft.settings.tooltip.options_selected"

    /** 數值範圍的標題。 */
    const val RANGE: String = "mahjongcraft.settings.tooltip.range"

    /** 數值範圍：最小值與最大值。 */
    const val RANGE_VALUES: String = "mahjongcraft.settings.tooltip.range_values"

    /** 可不設定的數值範圍：不設定時的名稱、最小值與最大值。 */
    const val RANGE_VALUES_OPTIONAL: String = "mahjongcraft.settings.tooltip.range_values_optional"

    /** 不可選的選項：選項名稱與原因。 */
    const val UNAVAILABLE_OPTION: String = "mahjongcraft.settings.tooltip.unavailable_option"

    /** 開關的操作提示。 */
    const val HINT_TOGGLE: String = "mahjongcraft.settings.tooltip.hint.toggle"

    /** 數值欄位的加減按鈕操作提示，帶每次調整量。 */
    const val HINT_STEP: String = "mahjongcraft.settings.tooltip.hint.step"

    /** 數值欄位按住 Shift 的操作提示，帶每次調整量。 */
    const val HINT_SHIFT_STEP: String = "mahjongcraft.settings.tooltip.hint.shift_step"

    /** 數值欄位可直接輸入數字的操作提示。 */
    const val HINT_TYPE_NUMBER: String = "mahjongcraft.settings.tooltip.hint.type_number"

    /** 可不設定的數值欄位改為不設定的操作提示，帶最小值與不設定時的名稱。 */
    const val HINT_CLEAR: String = "mahjongcraft.settings.tooltip.hint.clear"

    /** 開啟選項畫面的操作提示。 */
    const val HINT_CHOOSE: String = "mahjongcraft.settings.tooltip.hint.choose"
}
