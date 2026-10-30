package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/**
 * 設定畫面與聊天摘要共用的受控欄位值。
 */
sealed interface GameConfigPresentationValue {
    /**
     * 布林設定值。
     *
     * @property enabled 布林設定值。
     */
    data class BooleanValue(
        val enabled: Boolean,
    ) : GameConfigPresentationValue

    /**
     * 可為空的整數設定值。
     *
     * @property number 整數設定值。
     */
    data class IntegerValue(
        val number: Int?,
    ) : GameConfigPresentationValue

    /**
     * 選項設定值。
     *
     * @property optionId 選項 ID。
     */
    data class ChoiceValue(
        val optionId: String,
    ) : GameConfigPresentationValue

    /**
     * 多選設定值，集合的迭代順序不影響是否為相同設定。
     *
     * @property optionIds 已選取的完整 Namespaced ID 集合。
     */
    data class MultiChoiceValue(
        val optionIds: Set<String>,
    ) : GameConfigPresentationValue
}
