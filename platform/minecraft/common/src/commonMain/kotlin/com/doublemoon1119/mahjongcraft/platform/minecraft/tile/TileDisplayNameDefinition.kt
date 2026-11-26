package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * 將牌種 ID 綁定到顯示名稱的翻譯 key。
 *
 * @property typeId 牌種的穩定識別碼。
 * @property translationKey 對應顯示文字的翻譯 key。
 */
data class TileDisplayNameDefinition(
    val typeId: TileTypeId,
    val translationKey: String,
)
