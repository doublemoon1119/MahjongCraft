package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 將規則模組綁定到該規則使用的預設牌組外觀。
 *
 * @property ruleModuleId 規則模組的完整 `namespace:path` 識別碼。
 * @property appearanceId 已登記牌組外觀的完整 `namespace:path` 識別碼。
 */
data class MinecraftRuleTileAppearanceDefinition(
    val ruleModuleId: String,
    val appearanceId: String,
) {
    init {
        NamespacedId.requireValid(ruleModuleId) { "Invalid rule module ID: $ruleModuleId" }
        NamespacedId.requireValid(appearanceId) { "Invalid tile set appearance ID: $appearanceId" }
    }
}
