package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationCategory
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSource
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.ScoringStickTablePropKind

/**
 * 第三方 mod 登記 Fabric 1.20.1 專屬整合的契約，例如需要自己的 entity 才能呈現的規則桌面物件種類。
 *
 * 發現方式與 `MinecraftMahjongExtension` 相同：同一個類別同時實作 `MahjongExtension` 與這個介面即可，
 * loader 從 `MahjongExtension` entrypoint 的掃描結果中篩選出有實作這個介面的部分。
 */
interface FabricMahjongExtension {
    /** 第三方 extension 的穩定識別字串，用於診斷註冊錯誤。 */
    val id: String

    /**
     * 登記規則桌面物件種類，說明描述裡的種類識別碼要生成哪種 entity、如何找回既有的 entity。
     *
     * 預設不登記；只使用內建種類的 extension 不必實作。
     */
    fun registerTablePropKinds(registry: FabricTablePropKindRegistry) = Unit
}

/** 表示指定 Fabric extension 無法完成 registry 註冊。 */
class FabricMahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Fabric Mahjong extension: $extensionId", cause)

/** 啟動報告中規則桌面物件種類的分類識別碼。 */
internal const val TABLE_PROP_KIND_CATEGORY_ID: String = "mahjongcraft:table_prop_kind"

/**
 * 先登記內建的點棒種類 [ScoringStickTablePropKind]，再依 [extensions] 順序登記各 extension 的種類，全部成功後凍結。
 *
 * @return 依來源分組的所有種類：內建種類在前，接著依 [extensions] 順序排列每個 extension。
 * @throws FabricMahjongExtensionRegistrationException 若任一 extension 登記失敗。
 */
internal fun FabricTablePropKindRegistry.registerAndFreeze(extensions: Iterable<FabricMahjongExtension>): List<ExtensionRegistrationSource> {
    register(ScoringStickTablePropKind)
    var knownIds = kinds.map { it.id }.toSet()
    val sources = mutableListOf(tablePropKindSource(extensionId = null, kindIds = knownIds))
    val registeredExtensionIds = mutableSetOf<String>()
    extensions.forEach { extension ->
        if (!registeredExtensionIds.add(extension.id)) {
            throw FabricMahjongExtensionRegistrationException(
                extensionId = extension.id,
                cause = IllegalArgumentException("Duplicate Fabric Mahjong extension id: ${extension.id}"),
            )
        }
        try {
            extension.registerTablePropKinds(this)
        } catch (cause: Exception) {
            throw FabricMahjongExtensionRegistrationException(extension.id, cause)
        }
        val afterIds = kinds.map { it.id }.toSet()
        sources += tablePropKindSource(extensionId = extension.id, kindIds = afterIds - knownIds)
        knownIds = afterIds
    }
    freeze()
    return sources
}

/** 建立一個來源登記的規則桌面物件種類；沒有種類時不含任何類別。 */
private fun tablePropKindSource(extensionId: String?, kindIds: Set<String>): ExtensionRegistrationSource = ExtensionRegistrationSource(
    extensionId = extensionId,
    categories = listOfNotNull(
        kindIds.takeIf { it.isNotEmpty() }?.let { ExtensionRegistrationCategory(TABLE_PROP_KIND_CATEGORY_ID, "Table Prop Kind", it.sorted()) },
    ),
)
