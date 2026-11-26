package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import kotlin.uuid.Uuid

/**
 * 房間成員外觀 provider 可見的公開身分。
 *
 * @property playerId 成員 UUID。
 * @property isAi 成員是否由 AI 操控。
 * @property character 已授權的角色描述，未提供時沿用既有來源。
 * @property hasLiveModel 是否有目前已授權且載入的本人可供預覽。
 */
data class RoomMemberAppearanceContext(
    val playerId: Uuid,
    val isAi: Boolean,
    val character: AiCharacterDefinition? = null,
    val hasLiveModel: Boolean = false,
)
