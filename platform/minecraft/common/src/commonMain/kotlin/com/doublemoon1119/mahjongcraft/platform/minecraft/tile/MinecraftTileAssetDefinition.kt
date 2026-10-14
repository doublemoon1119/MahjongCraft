package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * 將牌種 ID 綁定到 Minecraft 牌面資源 key，供平台解析貼圖。
 *
 * @property typeId 牌種的穩定識別碼。
 * @property assetKey 牌面資源的穩定識別碼。
 * @property resources 貼圖與模型的完整資源 ID；省略時沿用內建 `mahjongcraft` 資源路徑。
 */
data class MinecraftTileAssetDefinition(
    val typeId: TileTypeId,
    val assetKey: String,
    val resources: MinecraftTileResources = MinecraftTileResources.builtIn(assetKey),
)
