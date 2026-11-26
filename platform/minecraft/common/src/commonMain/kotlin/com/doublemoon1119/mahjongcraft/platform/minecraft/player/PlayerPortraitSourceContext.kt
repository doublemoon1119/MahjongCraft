package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import kotlin.uuid.Uuid

/**
 * 第三方 portrait provider 可見的規則中立玩家身分。
 *
 * @property playerId 玩家的 UUID。
 * @property isAi 玩家是否由 AI 操控。
 * @property character 已授權的角色描述，未提供時沿用既有來源。
 */
data class PlayerPortraitSourceContext(
    val playerId: Uuid,
    val isAi: Boolean,
    val character: AiCharacterDefinition? = null,
)
