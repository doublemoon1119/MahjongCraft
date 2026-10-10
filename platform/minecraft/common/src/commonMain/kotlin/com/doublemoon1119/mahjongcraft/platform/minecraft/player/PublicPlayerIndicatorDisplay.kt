package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/**
 * 玩家相關公開標記在 Minecraft 面板使用的本地化名稱與 RGB 顏色。
 *
 * 公開標記包括座位上的公開 indicator 與其 option，以及胡牌結算排行中的付款原因；兩者都是規則提供、
 * 所有玩家與旁觀者都能看到的完整 namespaced ID。
 *
 * @property translationKey 公開標記名稱的翻譯鍵。
 * @property colorRgb 顯示公開標記時使用的 RGB 顏色。
 */
data class PublicPlayerIndicatorDisplay(
    val translationKey: String,
    val colorRgb: Int = 0xFFE08A,
) {
    init {
        require(translationKey.isNotBlank()) { "Public indicator translation key must not be blank" }
        require(colorRgb in 0..0xFFFFFF) { "Public indicator color must be RGB" }
    }
}
