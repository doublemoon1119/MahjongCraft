package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.registerBuiltInTablePropKinds

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

/** 表示指定第三方 Fabric extension 無法完成 registry 註冊。 */
class FabricMahjongExtensionRegistrationException(
    extensionId: String,
    cause: Throwable,
) : IllegalStateException("Failed to register Fabric Mahjong extension: $extensionId", cause)

/** 啟動報告中第三方規則桌面物件種類的分類識別碼。 */
internal const val TABLE_PROP_KIND_CATEGORY_ID: String = "mahjongcraft:table_prop_kind"

/**
 * 先登記內建 [FabricTablePropKind]，再依 [extensions] 順序登記第三方種類，全部成功後凍結。
 *
 * @return 第三方登記的種類識別碼，依登記順序排列，不含內建種類。
 * @throws FabricMahjongExtensionRegistrationException 若任一 extension 登記失敗。
 */
internal fun FabricTablePropKindRegistry.registerAndFreeze(extensions: Iterable<FabricMahjongExtension>): List<String> {
    registerBuiltInTablePropKinds()
    val baseline = registrationKeys
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
    }
    freeze()
    return kinds.map { it.id }.filterNot { it in baseline }
}
