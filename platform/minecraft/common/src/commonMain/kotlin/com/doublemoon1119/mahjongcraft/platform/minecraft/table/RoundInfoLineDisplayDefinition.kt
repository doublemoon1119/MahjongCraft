package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * RoundInfoLineDisplay registry 的翻譯資訊登記描述。
 *
 * @property key 局況行的完整 `namespace:path` 識別碼，與翻譯鍵分開。
 * @property display 局況行的翻譯資訊。
 */
data class RoundInfoLineDisplayDefinition(
    val key: String,
    val display: RoundInfoLineDisplay,
)
