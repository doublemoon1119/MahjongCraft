package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 將牌面資源 key 綁定到文字牌面，供聊天與文字呈現使用。
 *
 * @property assetKey 牌面資源的穩定識別碼。
 * @property emoji 對應的文字牌面。
 */
data class TileEmojiDefinition(
    val assetKey: String,
    val emoji: String,
)
