package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterLimits

/**
 * 一名 AI 在開局時固定的角色來源；未知角色保留 ID，但不猜測描述。
 *
 * @property characterId 當時選定的完整角色 ID。
 * @property definition 當時可取得的描述；未安裝提供者時為 null。
 */
data class AiCharacterSnapshot(
    val characterId: String,
    val definition: AiCharacterDefinition?,
) {
    init {
        AiCharacterLimits.requireId(characterId)
        require(definition == null || definition.id == characterId) { "Character snapshot identity does not match its definition" }
    }
}
