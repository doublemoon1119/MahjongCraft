package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/**
 * 依 asset key 取得材質資源相對路徑（相對 `assets/<namespace>/`）。
 *
 * 這是內建資源的相對路徑慣例；第三方可透過 [MinecraftTileResources] 指定自己的完整資源 ID。
 *
 * @param assetKey 素材登記使用的穩定 key。
 * @return 材質資源相對路徑。
 */
fun tileTextureAssetPath(assetKey: String): String = "textures/item/mahjong_tile/mahjong_tile_$assetKey.png"

/**
 * 依 asset key 取得內建子模型資源相對路徑（相對 `assets/<namespace>/`）。
 *
 * @param assetKey 素材登記使用的穩定 key。
 * @return 子模型資源相對路徑。
 */
fun tileModelAssetPath(assetKey: String): String = "item/mahjong_tile/mahjong_tile_$assetKey"

/**
 * 取得牌面貼圖的完整資源 ID；已登記牌種使用其明確資源，其他 key 沿用內建資源路徑。
 *
 * @param assetKey 素材登記使用的穩定 key。
 * @param registry 用來查詢第三方資源映射的 registry。
 * @return 貼圖資源的完整 `namespace:path` ID。
 */
fun tileTextureResourceId(assetKey: String, registry: MinecraftTileAssetRegistry): String = assetKey.normalizedTileAssetKey(registry).let { key ->
    registry.findResources(key)?.textureId ?: "${MinecraftModMetadata.MOD_ID}:${tileTextureAssetPath(key)}"
}

/**
 * 取得牌面模型的完整資源 ID；已登記牌種使用其明確資源，其他 key 沿用內建資源路徑。
 *
 * @param assetKey 素材登記使用的穩定 key。
 * @param registry 用來查詢第三方資源映射的 registry。
 * @return 模型資源的完整 `namespace:path` ID。
 */
fun tileModelResourceId(assetKey: String, registry: MinecraftTileAssetRegistry): String = assetKey.normalizedTileAssetKey(registry).let { key ->
    registry.findResources(key)?.modelId ?: "${MinecraftModMetadata.MOD_ID}:${tileModelAssetPath(key)}"
}
