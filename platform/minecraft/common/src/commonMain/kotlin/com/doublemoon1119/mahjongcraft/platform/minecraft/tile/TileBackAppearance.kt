package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 取得蓋著的牌要畫的牌背外觀：牌面樣式指定了牌身時用該牌身，其餘使用規則的牌組外觀。
 *
 * 只有知道牌面時才能傳入真正的素材 key；看不到牌面的牌傳入未知牌的 key，結果就是規則外觀。
 *
 * @param assetKey 這張牌的牌面素材 key。
 * @param ruleAppearance 本局規則的牌組外觀。
 * @return 牌背使用的牌組外觀。
 */
fun MinecraftTileAssetRegistry.backAppearance(
    assetKey: String,
    ruleAppearance: MinecraftTileSetAppearanceDefinition,
): MinecraftTileSetAppearanceDefinition = findBodyAppearance(assetKey) ?: ruleAppearance
