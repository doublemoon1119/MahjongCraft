package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/**
 * 內建單選欄位的一個選項，明確對應選項 ID、顯示文字與設定值。
 *
 * @param T 選項對應的設定值型別。
 * @property id 選項的完整 namespaced ID。
 * @property translationKey 選項名稱的翻譯鍵。
 * @property value 選取此選項時寫入設定的值。
 */
internal data class BuiltInGameConfigOption<T>(
    val id: String,
    val translationKey: String,
    val value: T,
)
