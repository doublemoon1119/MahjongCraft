package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition

/**
 * 一隻已存在實體可參加麻將時使用的描述，不建立或接管實體。
 *
 * @property character 該實體目前的外觀；世界能力必須能借用指定實體。
 * @property strategyKey 預設打牌策略，仍由伺服器確認已登記。
 */
class FabricRoomParticipant(
    val character: AiCharacterDefinition,
    val strategyKey: String,
) {
    init {
        NamespacedId.requireValid(strategyKey) { "Invalid participant strategy ID" }
        require(character.worldCapabilityId != null) { "A seated participant needs a world capability" }
    }
}
