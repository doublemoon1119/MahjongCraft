package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterLimits

/** 保存不可變角色描述的登記表；相同 ID 不得覆寫。 */
class AiCharacterRegistryImpl : AiCharacterRegistry {
    /** 按完整 ID 索引的描述。 */
    private val entries = mutableMapOf<String, AiCharacterDefinition>()

    override var isFrozen: Boolean = false
        private set

    override val registrationKeys: Set<String> get() = entries.keys.toSet()

    override fun register(definition: AiCharacterDefinition) {
        check(!isFrozen) { "AI character registry is frozen" }
        require(definition.id !in entries) { "Duplicate AI character ID: ${definition.id}" }
        require(entries.size < AiCharacterLimits.MAX_CATALOGUE_SIZE) { "Too many AI characters" }
        entries[definition.id] = definition
    }

    override fun find(id: String): AiCharacterDefinition? = entries[id]

    override fun definitions(): List<AiCharacterDefinition> = entries.values.sortedBy { it.id }

    override fun freeze() {
        isFrozen = true
    }
}
