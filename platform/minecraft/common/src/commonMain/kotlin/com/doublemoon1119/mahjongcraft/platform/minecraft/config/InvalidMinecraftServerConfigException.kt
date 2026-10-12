package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/**
 * Server config TOML 解碼或驗證失敗。
 *
 * @param message 說明設定檔問題的診斷訊息。
 * @param cause 解碼或欄位驗證時的原始例外；沒有原始例外時為 null。
 */
class InvalidMinecraftServerConfigException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
