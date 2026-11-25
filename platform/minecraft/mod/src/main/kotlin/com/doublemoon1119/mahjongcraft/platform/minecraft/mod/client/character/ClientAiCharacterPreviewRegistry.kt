package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshot
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshotCategory
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.AiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.AiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.ExtensionRegistrationCategoryIds

/** 客戶端自己的預覽登記表，凍結前不執行任何工廠。 */
internal class ClientAiCharacterPreviewRegistry : AiCharacterPreviewRegistrar {
    /** 依完整 ID 保存能力。 */
    private val entries = linkedMapOf<String, AiCharacterPreviewDefinition>()

    override val registrationKeys: Set<String> get() = entries.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: AiCharacterPreviewDefinition) {
        check(!isFrozen) { "Character preview registry is frozen" }
        require(definition.id !in entries) { "Duplicate character preview capability: ${definition.id}" }
        entries[definition.id] = definition
    }

    /**
     * 查詢已登記能力，不在此處執行工廠。
     *
     * @param id 完整識別碼。
     * @return 登記定義，沒有時為 null。
     */
    fun find(id: String): AiCharacterPreviewDefinition? = entries[id]

    /**
     * 沿既有登記報告格式捕捉獨立快照，不執行模型工廠。
     *
     * @return 可比較來源增量的登記快照。
     */
    fun registrationSnapshot(): ExtensionRegistrationSnapshot = ExtensionRegistrationSnapshot(
        categories = listOf(ExtensionRegistrationSnapshotCategory(ExtensionRegistrationCategoryIds.AI_CHARACTER_PREVIEW, "AI Character Preview", registrationKeys)),
    )

    /** 全部回呼成功後結束登記。 */
    fun freeze() {
        isFrozen = true
    }
}
