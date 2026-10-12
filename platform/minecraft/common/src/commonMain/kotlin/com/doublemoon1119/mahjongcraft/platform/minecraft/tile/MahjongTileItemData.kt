package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 麻將牌物品的牌面資料語意，不依賴物品資料的實際儲存方式。 */
object MahjongTileItemData {
    /** 牌面資料的欄位名稱。 */
    const val TILE_KEY: String = "tile"

    /**
     * 讀取並正規化物品儲存的牌面 key。
     *
     * @param storedKey 儲存的牌面 key；缺少時使用配方預設牌。
     * @param registry 可辨認有效牌面 key 的 registry。
     * @return 有效牌面 key；未知名稱轉為未知牌面 key。
     */
    fun read(storedKey: String?, registry: MinecraftTileAssetRegistry): String = storedKey?.normalizedTileAssetKey(registry) ?: STANDARD_TILE_ASSET_KEYS.first()

    /**
     * 寫入前正規化牌面名稱。
     *
     * @param assetKey 要寫入的牌面 key。
     * @param registry 可辨認有效牌面 key 的 registry。
     * @return 正規化後的牌面 key。
     */
    fun write(assetKey: String, registry: MinecraftTileAssetRegistry): String = assetKey.normalizedTileAssetKey(registry)
}
