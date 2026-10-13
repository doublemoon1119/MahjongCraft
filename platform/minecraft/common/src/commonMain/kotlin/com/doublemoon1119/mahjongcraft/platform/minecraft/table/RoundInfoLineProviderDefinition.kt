package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * RoundInfoLineDisplay registry 的規則 provider 登記描述。
 *
 * @property ruleModuleId 規則模組的穩定識別碼。
 * @property provider 依桌況建立局況行的 provider。
 */
data class RoundInfoLineProviderDefinition(
    val ruleModuleId: String,
    val provider: RoundInfoLineProvider,
)
