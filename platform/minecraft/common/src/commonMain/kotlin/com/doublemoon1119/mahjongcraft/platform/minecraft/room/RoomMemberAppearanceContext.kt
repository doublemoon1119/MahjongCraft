package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import kotlin.uuid.Uuid

/**
 * 房間成員外觀 provider 可見的公開身分。
 *
 * @property playerId 成員 UUID。
 * @property isAi 成員是否由 AI 操控。
 */
data class RoomMemberAppearanceContext(
    val playerId: Uuid,
    val isAi: Boolean,
)
