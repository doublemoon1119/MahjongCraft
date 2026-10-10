package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 可由 GUI、hover 與差異診斷共用的單一設定欄位。
 *
 * @property id 欄位的 Namespaced ID。
 * @property categoryId 所屬分類的 Namespaced ID。
 * @property nameTranslationKey 欄位名稱翻譯鍵。
 * @property descriptionTranslationKey 欄位描述翻譯鍵。
 * @property editor 欄位使用的編輯器。
 * @property isEditable 欄位是否可編輯。
 * @property isEnabled 依目前草稿決定欄位是否可操作。
 * @property read 從設定讀取欄位值。
 * @property update 以欄位值更新設定；可編輯欄位必須提供。
 */
data class GameConfigFieldDefinition(
    val id: String,
    val categoryId: String,
    val nameTranslationKey: String,
    val descriptionTranslationKey: String,
    val editor: GameConfigEditorSpec,
    val isEditable: Boolean,
    val isEnabled: (GameConfig) -> Boolean = { true },
    val read: (GameConfig) -> GameConfigPresentationValue,
    val update: ((GameConfig, GameConfigPresentationValue) -> GameConfig)? = null,
) {
    init {
        NamespacedId.requireValid(id) { "Config field ID must be namespaced: $id" }
        NamespacedId.requireValid(categoryId) { "Config field category ID must be namespaced: $categoryId" }
        require(nameTranslationKey.isNotBlank()) { "Config field name translation key must not be blank" }
        require(descriptionTranslationKey.isNotBlank()) { "Config field description translation key must not be blank" }
        require(!isEditable || update != null) { "Editable config field must provide an updater: $id" }
    }
}
