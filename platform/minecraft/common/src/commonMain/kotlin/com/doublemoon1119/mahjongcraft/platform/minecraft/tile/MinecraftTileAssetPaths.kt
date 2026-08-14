package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 麻將牌 asset key 對應資源路徑的命名慣例，與 loader 無關。
 *
 * 內建與第三方牌面共用同一套命名，只要素材檔放在慣例路徑即可被解析，不需額外註冊模型檔。
 */

/** 依 asset key 取得材質資源相對路徑（相對 `assets/<namespace>/`）。 */
fun tileTextureAssetPath(assetKey: String): String = "textures/item/mahjong_tile/mahjong_tile_$assetKey.png"

/** 依 asset key 取得內建子模型資源相對路徑（相對 `assets/<namespace>/`）。 */
fun tileModelAssetPath(assetKey: String): String = "item/mahjong_tile/mahjong_tile_$assetKey"
