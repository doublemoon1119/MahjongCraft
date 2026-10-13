package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSource
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 將內建項目與第三方 Fabric extension 登記至 runtime registry，完成後統一凍結。 */
internal object FabricMahjongExtensionRegistrar {
    /**
     * 依固定順序執行內建 extension 與第三方 extension 的回呼，全部成功後凍結 registry。
     *
     * @param extensions 依序執行登記回呼的 Fabric extension。
     * @param registries extension 使用並於成功後凍結的 Fabric registry 集合。
     * @param builtIn 不屬於任何規則的 Fabric 內建 extension。
     * @return 依來源分組的登記資訊；內建登記的來源識別碼為 null。
     * @throws FabricMahjongExtensionRegistrationException 若任一 extension 登記失敗。
     */
    fun registerAndFreeze(
        extensions: Iterable<FabricMahjongExtension>,
        registries: FabricExtensionRegistries,
        builtIn: FabricMahjongExtension,
    ): List<ExtensionRegistrationSource> {
        val registeredExtensionIds = mutableSetOf<String>()
        registerCallbacks(builtIn, registries, registeredExtensionIds)
        var snapshot = registries.registrationSnapshot()
        val sources = mutableListOf(ExtensionRegistrationSource(extensionId = null, categories = snapshot.toCategories()))
        extensions.forEach { extension ->
            registerCallbacks(extension, registries, registeredExtensionIds)
            val after = registries.registrationSnapshot()
            sources += ExtensionRegistrationSource(extensionId = extension.id, categories = snapshot.additionsSince(after))
            snapshot = after
        }
        registries.freezeAll()
        return sources
    }

    /**
     * 驗證 extension 識別碼、拒絕重複後依固定順序執行兩個 Fabric 登記回呼。
     *
     * @param extension 要執行登記回呼的 extension。
     * @param registries 提供給 extension 回呼使用的 Fabric registry 集合。
     * @param registeredExtensionIds 已使用的 extension 識別碼集合。
     */
    private fun registerCallbacks(
        extension: FabricMahjongExtension,
        registries: FabricExtensionRegistries,
        registeredExtensionIds: MutableSet<String>,
    ) {
        try {
            NamespacedId.requireValid(extension.id) { "Fabric Mahjong extension ID must be namespaced: ${extension.id}" }
            require(registeredExtensionIds.add(extension.id)) {
                "Duplicate Fabric Mahjong extension id: ${extension.id}"
            }
            extension.registerTablePropKinds(registries.tablePropKindRegistry)
            extension.registerSeatProviders(registries.seatProviderRegistry)
        } catch (cause: Exception) {
            throw FabricMahjongExtensionRegistrationException(extension.id, cause)
        }
    }
}
