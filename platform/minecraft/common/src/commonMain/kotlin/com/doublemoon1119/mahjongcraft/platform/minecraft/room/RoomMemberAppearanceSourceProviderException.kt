package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/**
 * 保留失敗 provider ID，讓平台可去重記錄後安全 fallback。
 *
 * @property providerId 發生錯誤的 provider ID。
 * @param cause provider 拋出的原始例外。
 */
class RoomMemberAppearanceSourceProviderException(
    val providerId: String,
    cause: Throwable,
) : RuntimeException("Room member appearance provider failed: $providerId", cause)
