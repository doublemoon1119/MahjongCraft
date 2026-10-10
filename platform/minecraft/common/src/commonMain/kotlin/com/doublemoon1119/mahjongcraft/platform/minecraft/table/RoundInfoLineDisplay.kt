package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 規則專屬局況顯示行對應的翻譯資訊；局況行本身由同一 registry 中的規則呈現 provider 建立。
 *
 * @property translationKey 這一行文字使用的翻譯 key。
 */
data class RoundInfoLineDisplay(
    val translationKey: String,
)
