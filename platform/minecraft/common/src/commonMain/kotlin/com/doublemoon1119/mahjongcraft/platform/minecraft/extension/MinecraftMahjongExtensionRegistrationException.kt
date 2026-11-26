package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

/**
 * 表示指定 Minecraft extension 無法完成 registry 註冊。
 *
 * @param extensionId 無法完成註冊的 extension ID。
 * @param cause 導致註冊失敗的原始例外。
 */
class MinecraftMahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Minecraft Mahjong extension: $extensionId", cause)
