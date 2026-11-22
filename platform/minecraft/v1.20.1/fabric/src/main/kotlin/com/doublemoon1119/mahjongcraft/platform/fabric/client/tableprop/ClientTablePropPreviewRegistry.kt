package com.doublemoon1119.mahjongcraft.platform.fabric.client.tableprop

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshot
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshotCategory
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.TablePropPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.TablePropPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.ExtensionRegistrationCategoryIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropKindDefinition

/** 客戶端自己的桌上物件預覽登記表，凍結前不執行任何工廠。 */
internal class ClientTablePropPreviewRegistry : TablePropPreviewRegistrar {
    /** 依種類識別碼保存預覽。 */
    private val entries = linkedMapOf<String, TablePropPreviewDefinition>()

    override val registrationKeys: Set<String> get() = entries.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    /** 已登記種類的定義，供擺放計算使用。 */
    val definitions: Map<String, TablePropKindDefinition> get() = entries.mapValues { it.value.definition }

    override fun register(definition: TablePropPreviewDefinition) {
        check(!isFrozen) { "Table prop preview registry is frozen" }
        require(definition.id !in entries) { "Duplicate table prop preview: ${definition.id}" }
        entries[definition.id] = definition
    }

    /**
     * 查詢已登記的預覽，不在此處執行工廠。
     *
     * @param id 種類識別碼。
     * @return 登記的預覽，沒有時為 null。
     */
    fun find(id: String): TablePropPreviewDefinition? = entries[id]

    /**
     * 沿既有登記報告格式捕捉獨立快照，不執行工廠。
     *
     * @return 可比較來源增量的登記快照。
     */
    fun registrationSnapshot(): ExtensionRegistrationSnapshot = ExtensionRegistrationSnapshot(
        categories = listOf(ExtensionRegistrationSnapshotCategory(ExtensionRegistrationCategoryIds.TABLE_PROP_PREVIEW, "Table Prop Preview", registrationKeys)),
    )

    /** 全部回呼成功後結束登記。 */
    fun freeze() {
        isFrozen = true
    }
}
