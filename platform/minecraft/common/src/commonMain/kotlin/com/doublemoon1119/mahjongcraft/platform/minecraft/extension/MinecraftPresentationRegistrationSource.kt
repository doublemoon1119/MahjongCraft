package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

/**
 * 一個來源登記的 Minecraft 呈現映射。
 *
 * @property extensionId 登記這些映射的 extension ID；為 null 時代表內建映射。
 * @property categories 這個來源登記的非空類別。
 */
data class MinecraftPresentationRegistrationSource(
    val extensionId: String?,
    val categories: List<MinecraftPresentationRegistrationCategory>,
) {
    init {
        require(categories.none { it.registrationIds.isEmpty() }) { "Registration categories must not be empty" }
    }
}
