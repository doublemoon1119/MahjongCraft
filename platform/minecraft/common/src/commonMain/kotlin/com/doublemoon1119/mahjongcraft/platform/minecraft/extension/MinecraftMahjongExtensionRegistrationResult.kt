package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

/**
 * [MinecraftMahjongExtensionRegistrar.registerAndFreeze] 依來源分組的登記結果。
 *
 * @property sources 內建映射在前，接著依登記順序排列每個 extension。
 */
data class MinecraftMahjongExtensionRegistrationResult(
    val sources: List<MinecraftPresentationRegistrationSource>,
)
