package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/**
 * 第三方 provider 在解析期失敗；平台可依 [providerId] 去重警告並套用內建 fallback。
 *
 * @property providerId 失敗的 provider ID。
 * @param cause provider 解析時拋出的原始例外。
 */
class PlayerPortraitSourceProviderException(
    val providerId: String,
    cause: Throwable,
) : IllegalStateException("Player portrait provider failed: $providerId", cause)
