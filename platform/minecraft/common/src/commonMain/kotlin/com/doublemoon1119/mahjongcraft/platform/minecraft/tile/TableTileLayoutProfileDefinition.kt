package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 將規則模組 ID 綁定到桌面牌位配置。
 *
 * @property ruleModuleId 適用規則模組的穩定識別碼。
 * @property profile 該規則的桌面牌位配置。
 */
data class TableTileLayoutProfileDefinition(
    val ruleModuleId: String,
    val profile: TableTileLayoutProfile,
)
