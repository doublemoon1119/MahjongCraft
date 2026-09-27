package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 麻將牌物品的牌面資料語意，不依賴物品資料的實際儲存方式。 */
object MahjongTileItemData {
    /** 牌面資料的欄位名稱。 */
    const val TILE_KEY: String = "tile"

    /** 缺少牌面時使用配方預設牌；無效名稱使用未知牌面。 */
    fun read(storedKey: String?, registry: MinecraftTileAssetRegistry): String = storedKey?.normalizedTileAssetKey(registry) ?: ALL_TILE_ASSET_KEYS.first()

    /** 寫入前正規化牌面名稱。 */
    fun write(assetKey: String, registry: MinecraftTileAssetRegistry): String = assetKey.normalizedTileAssetKey(registry)
}
