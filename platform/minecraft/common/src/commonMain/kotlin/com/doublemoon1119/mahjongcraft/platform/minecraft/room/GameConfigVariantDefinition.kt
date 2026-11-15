package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 一個規則的玩法：為少數幾個設定欄位指定固定值的具名組合，例如把透明牌與不砌牌牆一起開啟。
 *
 * 玩法只描述「定義這個玩法的欄位」；選擇玩法時只改這些欄位，其他設定維持原值。同一規則各玩法指定過的
 * 欄位合起來就是該規則用來判斷玩法的欄位，某個玩法沒有指定其中的欄位時，該欄位取規則的預設值。
 * 每個規則另有一個自動提供的「標準」玩法，把這些欄位全部設為規則預設值，不需要登記。
 *
 * @property id 玩法的 Namespaced ID；[STANDARD_ID] 保留給自動提供的標準玩法。
 * @property nameTranslationKey 玩法名稱翻譯鍵。
 * @property descriptionTranslationKey 說明這個玩法套用哪些設定的翻譯鍵。
 * @property fieldValues 這個玩法指定的欄位 ID 與其值。
 */
data class GameConfigVariantDefinition(
    val id: String,
    val nameTranslationKey: String,
    val descriptionTranslationKey: String,
    val fieldValues: Map<String, GameConfigPresentationValue>,
) {
    init {
        NamespacedId.requireValid(id) { "Config variant ID must be namespaced: $id" }
        require(nameTranslationKey.isNotBlank()) { "Config variant name translation key must not be blank" }
        require(descriptionTranslationKey.isNotBlank()) { "Config variant description translation key must not be blank" }
    }

    /** 玩法的保留識別碼。 */
    companion object {
        /** 自動提供的標準玩法 ID；登記的玩法不可使用。 */
        const val STANDARD_ID: String = "mahjongcraft:standard"
    }
}
