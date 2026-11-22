package com.doublemoon1119.mahjongcraft.platform.fabric.extension

/**
 * 表示指定原生整合 extension 無法完成 registry 註冊。
 *
 * @param extensionId 無法完成註冊的擴充識別碼。
 * @param cause 註冊失敗的原因。
 */
class NativeMahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register native Mahjong extension: $extensionId", cause)
