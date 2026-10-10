package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** Server config TOML 解碼或驗證失敗。 */
class InvalidMinecraftServerConfigException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
