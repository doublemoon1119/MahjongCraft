package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 將「外觀樣式＋基本牌面」綁定到 Minecraft 牌面資源 key，例如赤五樣式的五萬使用 `m5_red`。
 *
 * 外觀樣式只改變外觀，不改變牌面或計分。牌張帶有未登記的樣式時，平台使用基本牌面的外觀。
 * 樣式可以另外指定牌身使用的牌組外觀，例如換成不同顏色的牌身；牌面仍使用 [resources]。
 *
 * @property styleId 牌張外觀樣式的完整 ID。
 * @property tile 套用樣式的基本牌面。
 * @property assetKey 牌面資源的穩定識別碼。
 * @property resources 貼圖與模型的完整資源 ID；省略時沿用內建 `mahjongcraft` 資源路徑。
 * @property bodyAppearanceId 牌身使用的已登記牌組外觀 ID；省略時使用規則的牌組外觀。
 */
data class MinecraftTileStyleAssetDefinition(
    val styleId: String,
    val tile: Tile,
    val assetKey: String,
    val resources: MinecraftTileResources = MinecraftTileResources.builtIn(assetKey),
    val bodyAppearanceId: String? = null,
) {
    init {
        NamespacedId.requireValid(styleId) { "Tile style ID must be namespaced: $styleId" }
        bodyAppearanceId?.let { NamespacedId.requireValid(it) { "Tile style body appearance ID must be namespaced: $it" } }
    }
}
