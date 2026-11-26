package com.doublemoon1119.mahjongcraft.extension

/**
 * 表示指定 extension 無法完成 runtime registry 註冊。
 *
 * @param extensionId 註冊失敗的 extension ID。
 * @param cause 導致註冊失敗的原因。
 */
class MahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Mahjong extension: $extensionId", cause)
