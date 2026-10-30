package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 依素材 key 尋找牌面角落標籤；外觀樣式的素材沒有自己的標籤時沿用基本牌面的標籤。
 *
 * @param assetKey 要繪製的素材 key。
 * @param assetRegistry 提供外觀樣式對應的基本牌面。
 * @return 可用的標籤；基本牌面也沒有時為 `null`。
 */
fun TileLabelRegistry.findFor(assetKey: String, assetRegistry: MinecraftTileAssetRegistry): TileLabel? = find(assetKey) ?: find(assetRegistry.baseAssetKey(assetKey))

/**
 * 依素材 key 尋找牌面 emoji；外觀樣式的素材沒有自己的 emoji 時沿用基本牌面的 emoji。
 *
 * @param assetKey 要顯示的素材 key。
 * @param assetRegistry 提供外觀樣式對應的基本牌面。
 * @return 可用的 emoji；基本牌面也沒有時為 `null`。
 */
fun TileEmojiRegistry.findFor(assetKey: String, assetRegistry: MinecraftTileAssetRegistry): String? = find(assetKey) ?: find(assetRegistry.baseAssetKey(assetKey))
