package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/**
 * 玩法固定其值、但仍顯示在設定頁的欄位；欄位顯示為不可操作，提示框寫出 [reasonTranslationKey] 的原因。
 *
 * @property value 玩法固定的值。
 * @property reasonTranslationKey 說明這個值為何固定的翻譯鍵，例如「這個玩法不使用赤寶牌」。
 */
data class GameConfigLockedField(
    val value: GameConfigPresentationValue,
    val reasonTranslationKey: String,
) {
    init {
        require(reasonTranslationKey.isNotBlank()) { "Locked config field reason translation key must not be blank" }
    }
}
