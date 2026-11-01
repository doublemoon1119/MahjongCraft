package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterCapabilityDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterCapabilityRegistrar

/** 保存初始化期間登記的角色能力，執行期只提供查詢。 */
internal class FabricAiCharacterCapabilityRegistry : FabricAiCharacterCapabilityRegistrar {
    /** 依完整識別碼保存能力。 */
    private val definitions = linkedMapOf<String, FabricAiCharacterCapabilityDefinition>()

    override val registrationKeys: Set<String>
        get() = definitions.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: FabricAiCharacterCapabilityDefinition) {
        check(!isFrozen) { "Character capability registry is frozen" }
        require(definitions.putIfAbsent(definition.id, definition) == null) { "Duplicate character capability: ${definition.id}" }
    }

    /**
     * 查詢角色宣告的原生能力。
     *
     * @param id 完整能力識別碼。
     * @return 登記的能力，否則為 null。
     */
    fun find(id: String): FabricAiCharacterCapabilityDefinition? = definitions[id]

    /** 初始化完成後停止登記。 */
    fun freeze() {
        isFrozen = true
    }
}
