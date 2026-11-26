package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 將牌面資源 key 綁定到角落標籤，供牌面顯示時查詢。
 *
 * @property assetKey 牌面資源的穩定識別碼。
 * @property label 牌面標籤的呈現描述。
 */
data class TileLabelDefinition(
    val assetKey: String,
    val label: TileLabel,
)
