package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 依 asset key 取得材質資源相對路徑（相對 `assets/<namespace>/`）。
 *
 * 內建與第三方牌面共用此命名慣例。
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
