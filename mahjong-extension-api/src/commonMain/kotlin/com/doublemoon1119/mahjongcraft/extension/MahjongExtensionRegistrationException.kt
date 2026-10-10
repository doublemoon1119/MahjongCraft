package com.doublemoon1119.mahjongcraft.extension

/** 表示指定 extension 無法完成 runtime registry 註冊。 */
class MahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Mahjong extension: $extensionId", cause)
