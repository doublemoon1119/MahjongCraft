package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 一個規則的玩法：玩家在設定頁上方選擇，決定規則的幾項固定玩法差異，例如把透明牌與不砌牌牆一起開啟。
 *
 * 玩法的欄位分成兩種：
 * - 識別欄位（[identifyingValues]）：決定目前是哪個玩法的欄位。這些欄位不顯示在設定頁，只能透過選擇玩法改變，
 *   所以從它們的值永遠能判斷出目前的玩法。同一規則的每個玩法都必須指定同一組識別欄位，值的組合彼此不同。
 * - 鎖定欄位（[lockedFields]）：玩法固定其值、但仍顯示在設定頁的欄位，顯示為不可操作並寫出原因。在其他玩法中
 *   可以自由調整。
 *
 * 選擇玩法時只改這兩種欄位，其他設定維持原值。
 *
 * 每個規則另有一個自動提供的「標準」玩法，不需要登記。它的識別欄位全部取規則預設值，沒有鎖定欄位。
 *
 * @property id 玩法的 Namespaced ID。[STANDARD_ID] 保留給自動提供的標準玩法。
 * @property nameTranslationKey 玩法名稱翻譯鍵。
 * @property descriptionTranslationKey 說明這個玩法內容的翻譯鍵。
 * @property identifyingValues 識別欄位的 ID 與這個玩法的值。
 * @property lockedFields 鎖定欄位的 ID 與固定值、原因。
 */
data class GameConfigVariantDefinition(
    val id: String,
    val nameTranslationKey: String,
    val descriptionTranslationKey: String,
    val identifyingValues: Map<String, GameConfigPresentationValue>,
    val lockedFields: Map<String, GameConfigLockedField> = emptyMap(),
) {
    init {
        NamespacedId.requireValid(id) { "Config variant ID must be namespaced: $id" }
        require(nameTranslationKey.isNotBlank()) { "Config variant name translation key must not be blank" }
        require(descriptionTranslationKey.isNotBlank()) { "Config variant description translation key must not be blank" }
        require(identifyingValues.keys.none { it in lockedFields }) { "Config variant $id locks one of its identifying fields" }
    }

    /** 玩法的保留識別碼。 */
    companion object {
        /** 自動提供的標準玩法 ID。登記的玩法不可使用。 */
        const val STANDARD_ID: String = "mahjongcraft:standard"
    }
}
