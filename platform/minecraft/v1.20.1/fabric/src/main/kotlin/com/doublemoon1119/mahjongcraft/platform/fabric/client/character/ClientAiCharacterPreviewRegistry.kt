package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshot
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshotCategory
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionRegistrationCategoryIds

/** 客戶端自己的預覽登記表，凍結前不執行任何工廠。 */
internal class ClientAiCharacterPreviewRegistry : FabricAiCharacterPreviewRegistrar {
    /** 依完整 ID 保存能力。 */
    private val entries = linkedMapOf<String, FabricAiCharacterPreviewDefinition>()

    override val registrationKeys: Set<String> get() = entries.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: FabricAiCharacterPreviewDefinition) {
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
    fun find(id: String): FabricAiCharacterPreviewDefinition? = entries[id]

    /**
     * 沿既有登記報告格式捕捉獨立快照，不執行模型工廠。
     *
     * @return 可比較來源增量的登記快照。
     */
    fun registrationSnapshot(): ExtensionRegistrationSnapshot = ExtensionRegistrationSnapshot(
        listOf(ExtensionRegistrationSnapshotCategory(FabricExtensionRegistrationCategoryIds.AI_CHARACTER_PREVIEW, "AI Character Preview", registrationKeys)),
    )

    /** 全部回呼成功後結束登記。 */
    fun freeze() {
        isFrozen = true
    }
}
