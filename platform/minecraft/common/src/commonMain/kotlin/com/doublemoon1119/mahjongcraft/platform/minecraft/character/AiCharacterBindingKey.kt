package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import kotlin.uuid.Uuid

/**
 * 一場對局中的角色代理身分；不使用實體 UUID 取代 AI 身分。
 *
 * @property tableId 桌子 UUID。
 * @property matchId 場次 UUID。
 * @property playerId AI UUID。
 */
data class AiCharacterBindingKey(
    val tableId: Uuid,
    val matchId: Uuid,
    val playerId: Uuid,
)
