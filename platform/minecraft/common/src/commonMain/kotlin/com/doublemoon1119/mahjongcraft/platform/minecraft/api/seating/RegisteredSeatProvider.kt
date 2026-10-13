package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating

/**
 * 依確定順序使用的提供者描述。
 *
 * @param W 世界型別。
 * @param R 乘坐者型別。
 * @property id Namespaced 註冊 ID。
 * @property provider 解析能力。
 * @property priority 數值較大者先解析。
 */
data class RegisteredSeatProvider<W, R>(
    val id: String,
    val provider: SeatProvider<W, R>,
    val priority: Int = 0,
)
