package com.doublemoon1119.mahjongcraft.platform.minecraft.history

/**
 * 歷史牌河上一種公開標記的呈現方式。
 *
 * @property labelTranslationKey 滑鼠提示中顯示的標記名稱。
 * @property sideways 帶有這個標記的牌是否橫擺。
 */
data class HistoryDiscardMarkerDisplay(
    val labelTranslationKey: String,
    val sideways: Boolean = false,
) {
    init {
        require(labelTranslationKey.isNotBlank()) { "History discard marker label must not be blank" }
    }
}
